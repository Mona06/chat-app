package custom_client.commands;

public class GameResponseCommand implements Command {
    private String inviter;

    private final boolean accepted;

    public GameResponseCommand(String inviter, boolean accepted) {
        this.inviter = inviter;
        this.accepted = accepted;
    }

    @Override
    public void collectCommandInformation() {
    }

    @Override
    public String formatToProtocolCommand() {
        if (inviter == null) {
            return null;
        }
        return String.format("GAME_INV_REQ {\"username\":\"%s\",\"accept\":%b}",
                inviter, accepted);
    }
}

