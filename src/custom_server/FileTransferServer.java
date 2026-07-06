package custom_server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class FileTransferServer {
    private final int basePort = 50000;

    private int nextPort = basePort;

    private final ConcurrentHashMap<String, FileTransferSession> activeTransfers = new ConcurrentHashMap<>();

    public FileTransferServer() {
        File tempDir = new File("temp_transfers");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
    }


    public synchronized int getAvailablePort() {
        return nextPort++;
    }

    public void initiateFileTransfer(ClientConnection sender, ClientConnection receiver, String filename, long fileSize, String checksum) {
        int transferPort = getAvailablePort();
        FileTransferSession session = new FileTransferSession(sender, receiver, transferPort, filename, fileSize, checksum);

        activeTransfers.put(sender.getUsername(), session);

        sender.sendMessage("INIT_FILE_RESP {\"status\": \"OK\", \"port\": " + transferPort + "}");

        receiver.sendMessage(String.format(
                "FILE_REQ {\"sender\":\"%s\",\"filename\":\"%s\",\"size\":%d}",
                sender.getUsername(), filename, fileSize
        ));

    }

    public void openFileSocket(String senderUsername) {
        FileTransferSession session = activeTransfers.get(senderUsername);
        if (session != null) {
            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(session.getPort())) {
                    System.out.println("Waiting for file transfer connection on port: " + session.getPort());
                    Socket fileSocket = serverSocket.accept();
                    handleFileTransfer(fileSocket, session);
                } catch (IOException e) {
                    // File transfer connection failed - client will be notified via timeout
                    System.out.println("[DEBUG] File transfer connection failed " + e.getMessage());
                } finally {
                    activeTransfers.remove(senderUsername);
                }
            }).start();
        }
    }

    private void handleFileTransfer(Socket socket, FileTransferSession session) {
        try (
                InputStream in = socket.getInputStream();
                FileOutputStream fos = new FileOutputStream("temp_transfers/" + session.getFilename())
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long remaining = session.getFileSize();

            while (remaining > 0 && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }

            System.out.println("File transfer complete: " + session.getFilename());

            // Now that the file is uploaded, we open the download socket for the receiver
            openDownloadSocket(session.getSender().getUsername(), session.getReceiver().getUsername());

            // Notifying the receiver that file is ready for download
            // Getting the download session to retrieve the port
            String downloadKey = session.getReceiver().getUsername() + "_download_" + session.getSender().getUsername();
            FileTransferSession downloadSession = activeTransfers.get(downloadKey);
            int downloadPort = (downloadSession != null) ? downloadSession.getPort() : 0;

            session.getReceiver().sendMessage(String.format("FILE_COMPLETE {\"sender\":\"%s\", \"filename\":\"%s\", \"checksum\":\"%s\", \"port\":%d}",
                    session.getSender().getUsername(), session.getFilename(), session.getChecksum(), downloadPort));
        } catch (IOException e) {
            // Notifying the sender that the file transfer failed
            session.getSender().sendMessage("FILE_TRANSFER_FAILED {\"status\":\"ERROR\"}");
        }
    }


    /**
     * Prepares a download session for the receiver to download the file
     *
     * @param sender       The username of the sender
     * @param receiver     The username of the receiver
     * @param downloadPort The port allocated for the receiver to download from
     */
    public void prepareDownloadSession(String sender, String receiver, int downloadPort) {
        FileTransferSession uploadSession = activeTransfers.get(sender);
        if (uploadSession != null) {
            // Creating a key for the download session
            String downloadKey = receiver + "_download_" + sender;
            FileTransferSession downloadSession = new FileTransferSession(
                    uploadSession.getSender(),
                    uploadSession.getReceiver(),
                    downloadPort,
                    uploadSession.getFilename(),
                    uploadSession.getFileSize(),
                    uploadSession.getChecksum()
            );
            activeTransfers.put(downloadKey, downloadSession);
        }
    }

    /**
     * Opens a socket for the receiver to download the file
     *
     * @param sender   The username of the sender
     * @param receiver The username of the receiver
     */
    public void openDownloadSocket(String sender, String receiver) {
        String downloadKey = receiver + "_download_" + sender;
        FileTransferSession session = activeTransfers.get(downloadKey);
        if (session != null) {
            CountDownLatch socketReady = new CountDownLatch(1);

            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(session.getPort())) {
                    System.out.println("Waiting for receiver to download on port: " + session.getPort());

                    // Signaling that the socket is ready
                    socketReady.countDown();

                    // Small delay to ensure accept() is ready to receive connections
                    Thread.sleep(50);

                    Socket receiverSocket = serverSocket.accept();
                    serveFileToReceiver(receiverSocket, session);
                } catch (IOException e) {
                    // Download socket failed - receiver will timeout
                    socketReady.countDown(); // Signal even on error
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // Thread interrupted - cleanup will happen in finally
                } finally {
                    activeTransfers.remove(downloadKey);
                    // Cleaning up temp file
                    new File("temp_transfers/" + session.getFilename()).delete();
                }
            }).start();

            // Waiting for the socket to be ready before returning
            try {
                socketReady.await();
            } catch (InterruptedException e) {
                // Thread interrupted while waiting
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Serves the file to the receiver client
     *
     * @param socket  The socket connected to the receiver
     * @param session The file transfer session
     */
    private void serveFileToReceiver(Socket socket, FileTransferSession session) {
        try (
                FileInputStream fis = new FileInputStream("temp_transfers/" + session.getFilename());
                OutputStream out = socket.getOutputStream()
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            // System.out.println("File served to receiver: " + session.getFilename());

            // Notifying the sender that file was delivered
            session.getSender().sendMessage(String.format(
                    "FILE_DELIVERED {\"receiver\":\"%s\", \"filename\":\"%s\"}",
                    session.getReceiver().getUsername(),
                    session.getFilename()
            ));
        } catch (IOException e) {
            System.err.println("Error while serving file: " + e.getMessage());
        }
    }

    public void cleanupClientTransfers(String username) {
        activeTransfers.entrySet().removeIf(entry -> {
            FileTransferSession session = entry.getValue();
            return session.getSender().getUsername().equals(username) ||
                    session.getReceiver().getUsername().equals(username);
        });
    }
}
