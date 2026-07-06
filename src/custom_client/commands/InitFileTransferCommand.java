package custom_client.commands;

import custom_client.FileTransferClient;
import custom_client.FileTransferManager;

import java.io.File;
import java.util.Scanner;

import static custom_client.utils.Util.rootDirectory;
import static custom_client.utils.Util.searchFile;

public class InitFileTransferCommand implements Command {
    private String receiver;

    private File file;

    private long size;

    private String checksum;

    @Override
    public void collectCommandInformation() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the username of the receiver of the file transfer:");
        receiver = scanner.nextLine().trim();

        if (receiver.isEmpty()) {
            System.out.println("Error: Receiver username cannot be empty.");
            return;
        }

        System.out.println("Enter the name of the file to be sent:");
        String filename = scanner.nextLine().trim();

        if (filename.isEmpty()) {
            System.out.println("Error: Filename cannot be empty.");
            return;
        }

        file = searchFile(new File(rootDirectory), filename);

        if (file == null) {
            System.out.println("Error: File not found in project directories.");
            return;
        }
        try {
            FileTransferManager manager = FileTransferManager.getInstance();
            FileTransferClient fileTransferClient = new FileTransferClient();

            size = file.length();
            if (size == 0) {
                System.out.println("Error: Cannot send empty file.");
                return;
            }

            System.out.println("Calculating file checksum... (this may take a moment for large files)");
            checksum = fileTransferClient.prepareFileTransfer(file);
            if (checksum == null || checksum.isEmpty()) {
                System.out.println("Error: Failed to calculate file checksum.");
                return;
            }
            System.out.println("Checksum calculated successfully.");

            manager.setCurrentTransfer(receiver, file);
            manager.setCurrentChecksum(checksum);

        } catch (Exception e) {
            System.err.println("Error preparing file transfer: " + e.getMessage());
        }
    }

    @Override
    public String formatToProtocolCommand() {
        return String.format(
                "INIT_FILE_REQ {\"receiver\": \"%s\", \"filename\": \"%s\", \"size\": \"%d\", \"checksum\": \"%s\"}",
                receiver,
                file.getName(),
                size,
                checksum
        );
    }


}
