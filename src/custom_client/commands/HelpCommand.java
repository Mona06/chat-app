package custom_client.commands;

public class HelpCommand implements Command {
    @Override
    public void collectCommandInformation() {
        System.out.println("\n===========================================");
        System.out.println("CHAT MENU");
        System.out.println("===========================================");
        System.out.println("1: Enter <username> - Enter the chat room");
        System.out.println("2: Broadcast <message> - Send a message to all users in the chat room");
        System.out.println("3: Leave - Leave the chat room");
        System.out.println("4: List users - List all connected users");
        System.out.println("5: Send PM <receiver> <message> - Send a private message to another user");
        System.out.println("6: Start game <opponent> - Start a heads or tails game");
        System.out.println("7: Send file <receiver> <filename> - Start a file transfer");
        System.out.println("HELP - Display this help menu");
    }

    @Override
    public String formatToProtocolCommand() {
        return null;
    }
}
