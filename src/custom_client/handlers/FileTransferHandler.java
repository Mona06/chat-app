package custom_client.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import custom_client.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static custom_client.utils.Util.formatFileSize;

public class FileTransferHandler implements LineHandler {
    private final FileTransferClient fileTransferClient;

    private final FileTransferManager fileTransferManager;

    private final Map<String, PendingFileTransfer> pendingTransfers;

    private int transferPort;

    private final ExecutorService fileTransferExecutor;

    public FileTransferHandler() {
        this.fileTransferClient = new FileTransferClient();
        this.fileTransferManager = FileTransferManager.getInstance();
        this.pendingTransfers = new HashMap<>();
        this.fileTransferExecutor = Executors.newCachedThreadPool();

        String downloadDirectory = "downloads/";
        File dir = new File(downloadDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Shuts down the executor service
     * Should be called when the client disconnects
     */
    public void shutdown() {
        fileTransferExecutor.shutdownNow();
    }

    @Override
    public void handleLine(String line) {
        final IncomingServerResponse response = new IncomingServerResponse(line);
        final JsonNode json = response.getJsonNode();

        if (response.errorCode != null) {
            IncomingServerResponse.handleError(response);
            return;
        }

        switch (response.responseType) {
            case INIT_FILE_RESP:
                handleFileInitResponse(json);
                break;
            case FILE_REQ:
                handleFileRequest(json);
                break;
            case FILE_INV_RESP:
                handleFileTransferInvResponse(json);
                break;
            case FILE_INV_REPLY:
                handleFileInvitationReply(json);
                break;
            case FILE_COMPLETE:
                handleFileComplete(json);
                break;
            case FILE_DELIVERED:
                handleFileDelivered(json);
                break;
            default:
                break;
        }
    }


    private void handleFileRequest(JsonNode json) {
        try {
            String sender = json.get("sender").asText();
            String filename = json.get("filename").asText();
            long size = json.get("size").asLong();

            PendingFileTransfer pendingTransfer = new PendingFileTransfer(sender, filename, size);
            pendingTransfers.put(sender, pendingTransfer);
            fileTransferManager.setPendingInviter(sender);

            System.out.println("\nUser " + sender + " wants to send you file: " + filename + " (" + formatFileSize(size) + ")");
            System.out.println("Type 'yes' to accept or 'no' to decline");
        } catch (Exception e) {
            System.err.println("Error processing file request: " + e.getMessage());
        }
    }

    private void handleFileTransferInvResponse(JsonNode json) {
        if ("OK".equals(json.get("status").asText())) {
            if (json.has("port")) {
                // Receiver accepted - storing the download port
                int downloadPort = json.get("port").asInt();
                String sender = fileTransferManager.getPendingInviter();

                // Storing the download port in pending transfer
                PendingFileTransfer transfer = pendingTransfers.get(sender);
                if (transfer != null) {
                    transfer.setPort(downloadPort);
                }

                System.out.println("File transfer accepted. Preparing to download...");
            } else {
                // No port means receiver declined or sender acknowledgment
                System.out.println("File transfer declined.");
            }
        } else {
            System.out.println("File transfer initialization failed: " + json.get("code").asText());
            fileTransferManager.clearCurrentTransfer();
        }
    }

    private void handleFileInvitationReply(JsonNode json) {
        boolean accepted = json.get("accepted").asBoolean();

        final File file = fileTransferManager.getCurrentFile();
        final int port = this.transferPort;

        if (accepted) {
            System.out.println("Receiver accepted. Starting file upload in background...");
            fileTransferExecutor.submit(() -> {
                try {
                    fileTransferClient.sendFile("localhost", port, file);
                    System.out.println("File upload completed successfully!");
                } catch (Exception e) {
                    System.err.println("Error during file upload: " + e.getMessage());
                }
            });
        } else {
            System.out.println("Receiver declined the file transfer.");
            fileTransferManager.clearCurrentTransfer();
        }
    }

    private void handleFileInitResponse(JsonNode json) {
        if ("OK".equals(json.get("status").asText())) {
            if (json.has("port")) {
                this.transferPort = json.get("port").asInt();
            }
            System.out.println("File transfer invitation sent. Waiting for response...");
        } else {
            System.out.println("File transfer initialization failed: " + json.get("code").asText());
            fileTransferManager.clearCurrentTransfer();
        }
    }

    private void handleFileComplete(JsonNode json) {
        try {
            final String sender = json.get("sender").asText();
            final String filename = json.get("filename").asText();
            final String checksum = json.get("checksum").asText();

            // Using port from FILE_COMPLETE if available, otherwise falling back to stored port
            int port = 0;
            if (json.has("port")) {
                port = json.get("port").asInt();
                System.out.println("Using download port from FILE_COMPLETE: " + port);
            } else {
                // Fallback to stored port from FILE_INV_RESP
                PendingFileTransfer transfer = pendingTransfers.get(sender);
                if (transfer != null) {
                    port = transfer.port;
                    System.out.println("Using download port from FILE_INV_RESP: " + port);
                }
            }

            if (port > 0) {
                final int downloadPort = port;
                // Downloading the file in background
                System.out.println("Starting background download from " + sender + ": " + filename);
                fileTransferExecutor.submit(() -> {
                    try {
                        boolean success = fileTransferClient.receiveFile(
                                "localhost",
                                downloadPort,
                                filename,
                                "downloads/",
                                checksum
                        );

                        if (success) {
                            System.out.println("Download complete and verified!");
                        } else {
                            System.out.println("Download failed or checksum mismatch.");
                        }
                    } catch (Exception e) {
                        System.err.println("Error during file download: " + e.getMessage());
                    } finally {
                        // Cleaning up
                        pendingTransfers.remove(sender);
                        fileTransferManager.clearPendingInviter();
                    }
                });
            } else {
                System.out.println("Received FILE_COMPLETE but no download port available.");
            }
        } catch (Exception e) {
            System.err.println("Error handling file complete notification: " + e.getMessage());
        }
    }

    private void handleFileDelivered(JsonNode json) {
        try {
            String receiver = json.get("receiver").asText();
            String filename = json.get("filename").asText();

            System.out.println("File successfully delivered to " + receiver + ": " + filename);
        } catch (Exception e) {
            System.err.println("Error handling file delivered notification: " + e.getMessage());
        }
    }


    /**
     * Helper class to store pending file transfer details
     */
    private static class PendingFileTransfer {
        private final String sender;

        private final String filename;

        private final long size;

        private int port = 9000;

        public PendingFileTransfer(String sender, String filename, long size) {
            this.sender = sender;
            this.filename = filename;
            this.size = size;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}