package custom_client.commands;

import java.util.Scanner;

public class PmCommand implements Command {
    String receiver;

    String message;

    @Override
    public void collectCommandInformation() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the name of the user you want to message: ");
        receiver = scanner.nextLine();
        System.out.println("Enter the message you want to send: ");
        message = scanner.nextLine();
        if (message == null || message.trim().isEmpty()) {
            System.out.println("Cannot send an empty message");
        }

    }

    @Override
    public String formatToProtocolCommand() {
        return String.format(
                "PM_REQ {\"receiver\":\"%s\",\"message\":\"%s\"}",
                receiver,
                message
        );
    }
}
