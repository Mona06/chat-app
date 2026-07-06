package custom_client.commands;

public class ClientListCommand implements Command {

    @Override
    public void collectCommandInformation() {
    }

    @Override
    public String formatToProtocolCommand() {
        return "CL_REQ";
    }
}
