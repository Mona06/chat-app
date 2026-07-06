package custom_client.commands;

import java.util.Scanner;

public class BroadcastCommand implements Command {

    String message;

    @Override
    public void collectCommandInformation() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the message you want to broadcast: ");
        message = scanner.nextLine();
    }

    @Override
    public String formatToProtocolCommand() {
        return String.format("BROADCAST_REQ {\"message\": \"%s\"}", message);
    }
}
