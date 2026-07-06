package custom_client;

import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;

public class FileTransferClient {
    private static final int BUFFER_SIZE = 8192;

    public void sendFile(String serverAddress, int port, File file) {
        try (Socket fileSocket = new Socket(serverAddress, port);
             FileInputStream fileInputStream = new FileInputStream(file);
             OutputStream outputStream = fileSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            System.out.println("File sent successfully.");
        } catch (IOException e) {
            System.err.println("Error sending file: " + e.getMessage());
        }
    }

    /**
     * Downloads a file from the server
     *
     * @param serverAddress    The server address to connect to
     * @param port             The port to connect to
     * @param filename         The name of the file to save
     * @param downloadPath     The directory path to save the file (must end with /)
     * @param expectedChecksum The expected SHA-256 checksum for verification
     * @return true if download successful and checksum matches, false otherwise
     */
    public boolean receiveFile(String serverAddress, int port, String filename,
                               String downloadPath, String expectedChecksum) {
        int maxRetries = 5;
        int retryDelayMs = 100;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Socket socket = new Socket(serverAddress, port);
                 InputStream in = socket.getInputStream();
                 FileOutputStream fos = new FileOutputStream(downloadPath + filename)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }

                // Verifying checksum
                File downloadedFile = new File(downloadPath + filename);
                String actualChecksum = calculateSHA256(downloadedFile);

                if (expectedChecksum.equals(actualChecksum)) {
                    System.out.println("File downloaded successfully: " + filename);
                    System.out.println("Checksum verified: " + actualChecksum);
                    return true;
                } else {
                    System.out.println("Checksum mismatch! Expected: " + expectedChecksum +
                            ", Got: " + actualChecksum);
                    downloadedFile.delete();
                    return false;
                }
            } catch (java.net.ConnectException e) {
                if (attempt < maxRetries) {
                    System.out.println("Connection refused, retrying (" + attempt + "/" + maxRetries + ")...");
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    System.err.println("Error downloading file after " + maxRetries + " attempts: " + e.getMessage());
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Error downloading file: " + e.getMessage());
                return false;
            }
        }
        return false;
    }


    /**
     * Calculates the SHA-256 hash of a file
     *
     * @param file The file to hash
     * @return The SHA-256 hash as a hex string
     */
    public static String calculateSHA256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        try (FileInputStream fis = new FileInputStream(file)) {
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    /**
     * Initiates a file transfer by sending the INIT_FILE_REQ message
     * This method should be called from the FileTransferManager
     *
     * @param file The file to send
     * @return The checksum of the file
     */
    public String prepareFileTransfer(File file) throws Exception {
        return calculateSHA256(file);
    }
}