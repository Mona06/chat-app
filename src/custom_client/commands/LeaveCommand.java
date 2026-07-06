package custom_client.commands;

public class LeaveCommand implements Command {
    @Override
    public void collectCommandInformation() {
        System.out.println("Leaving the chat room...");
    }

    @Override
    public String formatToProtocolCommand() {
        return "BYE";
    }
}
