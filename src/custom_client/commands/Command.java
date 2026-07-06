package custom_client.commands;

/**
 * Interface that will be used to execute the `strategy` pattern for all the separate
 * commands that the user can input.
 */
public interface Command {
    void collectCommandInformation();

    String formatToProtocolCommand();
}
