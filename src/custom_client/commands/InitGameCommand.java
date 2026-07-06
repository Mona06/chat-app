package custom_client.commands;

import java.util.Scanner;

public class InitGameCommand implements Command {
    String receiver;

    @Override
    public void collectCommandInformation() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the name of the user you want to challenge: ");
        receiver = scanner.nextLine();
    }

    @Override
    public String formatToProtocolCommand() {
        return String.format("INIT_GAME_REQ {\"receiver\": \"%s\"}", receiver);
    }
}
