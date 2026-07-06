package custom_client;

import custom_client.commands.Command;
import custom_client.commands.FileTransferResponseCommand;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public class FileTransferManager {
    private static volatile FileTransferManager INSTANCE;

    private File currentFile;

    private String currentFilename;

    private String currentReceiver;

    private String currentChecksum;

    private AtomicReference<String> pendingInviter;


    private FileTransferManager() {
        pendingInviter = new AtomicReference<>(null);
    }

    public static FileTransferManager getInstance() {
        if (INSTANCE == null) {
            synchronized (FileTransferManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FileTransferManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Sets the current file transfer
     *
     * @param file     The file to transfer
     * @param receiver The username of the receiver
     */
    public void setCurrentTransfer(String receiver, File file) {
        this.currentReceiver = receiver;
        this.currentFile = file;
        this.currentFilename = file.getName();
    }

    /**
     * Sets the current file transfer checksum
     *
     * @param checksum The SHA-256 checksum of the file
     */
    public void setCurrentChecksum(String checksum) {
        this.currentChecksum = checksum;
    }

    public long getFileSize() {
        return currentFile.length();
    }

    public void clearCurrentTransfer() {
        this.currentFile = null;
        this.currentFilename = null;
        this.currentReceiver = null;
        this.currentChecksum = null;
    }

    /**
     * Gets the current file being transferred
     *
     * @return The current file
     */
    public File getCurrentFile() {
        return currentFile;
    }

    public String getCurrentFilename() {
        return currentFilename;
    }

    /**
     * Gets the receiver of the current file transfer
     *
     * @return The receiver username
     */
    public String getCurrentReceiver() {
        return currentReceiver;
    }


    public Command handleFileTransferResponse(String response) {
        String inviter = pendingInviter.get();

        if (inviter == null) {
            System.out.println("No pending file transfer to respond to.");
            return null;
        }

        boolean accepted = response.toLowerCase().trim().equals("yes");
        pendingInviter.set(null);


        return new FileTransferResponseCommand(inviter, accepted);
    }

    public void setPendingInviter(String inviter) {
        this.pendingInviter.set(inviter);
    }

    public String getPendingInviter() {
        return pendingInviter.get();
    }

    public void clearPendingInviter() {
        pendingInviter.set(null);
    }
}


