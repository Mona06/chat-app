package custom_client.commands;

import java.util.Scanner;

public class LogonCommand implements Command {
    String username;

    @Override
    public void collectCommandInformation() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your username: ");
        username = scanner.nextLine();
    }

    @Override
    public String formatToProtocolCommand() {
        return String.format("LOGON {\"username\": \"%s\"}", username);
    }
}
