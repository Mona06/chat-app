package custom_server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransferSession implements Runnable {
    private final int port;

    private final String filename;

    private final long fileSize;

    private final String checksum;

    private final ClientConnection sender;

    private final ClientConnection receiver;

    public FileTransferSession(ClientConnection sender, ClientConnection receiver, int port, String filename, long fileSize, String checksum) {
        this.sender = sender;
        this.receiver = receiver;
        this.port = port;
        this.filename = filename;
        this.fileSize = fileSize;
        this.checksum = checksum;
    }

    public ClientConnection getSender() {
        return sender;
    }

    public ClientConnection getReceiver() {
        return receiver;
    }

    public int getPort() {
        return this.port;
    }

    public String getFilename() {
        return this.filename;
    }

    public long getFileSize() {
        return this.fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Waiting for file transfer on port " + port);
            Socket clientSocket = serverSocket.accept();
            receiveFile(clientSocket);
        } catch (IOException e) {
            // File transfer session failed
            System.out.println("Error accepting file transfer on port " + port);
        }
    }

    private void receiveFile(Socket socket) throws IOException {
        try (InputStream in = socket.getInputStream();
             FileOutputStream fileOut = new FileOutputStream("received_" + filename)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (totalRead >= fileSize) break;
            }

            System.out.println("File received: " + filename);
        }
    }
}
