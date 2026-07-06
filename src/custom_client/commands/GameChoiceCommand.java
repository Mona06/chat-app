package custom_client.commands;

/**
 * Command for submitting a heads or tails choice during the game
 */
public class GameChoiceCommand implements Command {
    private final String choice;

    public GameChoiceCommand(String choice) {
        this.choice = choice;
    }

    @Override
    public void collectCommandInformation() {
    }

    @Override
    public String formatToProtocolCommand() {
        // Converting text choice to numeric code (0=heads, 1=tails)
        int choiceCode;
        if (choice.equalsIgnoreCase("heads")) {
            choiceCode = 0;
        } else if (choice.equalsIgnoreCase("tails")) {
            choiceCode = 1;
        } else {
            // Invalid choice, but letting the server handle the error
            choiceCode = -1;
        }
        return String.format("GAME_CHOICE_REQ {\"choice\": %d}", choiceCode);
    }
}