package custom_client.commands;

public class FileTransferResponseCommand implements Command {
    private boolean accepted;

    private String inviter;

    public FileTransferResponseCommand(String inviter, boolean accepted) {
        this.inviter = inviter;
        this.accepted = accepted;
    }

    @Override
    public void collectCommandInformation() {

    }

    @Override
    public String formatToProtocolCommand() {
        return String.format("FILE_INV_REQ {\"username\":\"%s\",\"accept\":%b}",
                inviter, accepted);
    }
}
