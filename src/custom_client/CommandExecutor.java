package custom_client;

import custom_client.commands.*;

import java.util.HashMap;
import java.util.Map;

public class CommandExecutor {
    private final Map<String, Command> commands;
    private final ClientGameStateManager gameManager;
    private final FileTransferManager fileTransferManager;

    public CommandExecutor() {
        this.gameManager = ClientGameStateManager.getInstance();
        this.fileTransferManager = FileTransferManager.getInstance();
        this.commands = initializeCommands();
    }

    private Map<String, Command> initializeCommands() {
        Map<String, Command> cmds = new HashMap<>();
        cmds.put("help", new HelpCommand());
        cmds.put("1", new LogonCommand());
        cmds.put("2", new BroadcastCommand());
        cmds.put("3", new LeaveCommand());
        cmds.put("4", new ClientListCommand());
        cmds.put("5", new PmCommand());
        cmds.put("6", new InitGameCommand());
        cmds.put("7", new InitFileTransferCommand());
        return cmds;
    }

    public String handleCommand(String line) {
        // Handling pending game invitation response
        if (gameManager.getPendingInviter() != null) {
            Command responseCommand = gameManager.handleGameResponse(line);
            if (responseCommand != null) {
                return responseCommand.formatToProtocolCommand();
            }
            return null;
        }

        // Handling game choices during active game
        if (gameManager.isInGame() && gameManager.isWaitingForChoice() && !gameManager.isChoiceSubmitted()) {
            String input = line.trim().toLowerCase();
            if (input.equals("heads") || input.equals("tails")) {
                Command choiceCommand = gameManager.handleChoiceInput(line);
                if (choiceCommand != null) {
                    return choiceCommand.formatToProtocolCommand();
                }
                return null;
            }
        }

        // Handling file transfer responses
        if (fileTransferManager.getPendingInviter() != null) {
            Command responseCommand = fileTransferManager.handleFileTransferResponse(line);
            if (responseCommand != null) {
                return responseCommand.formatToProtocolCommand();
            }
            return null;
        }

        // Handling normal menu commands
        Command command = commands.get(line.toLowerCase().trim());
        if (command != null) {
            command.collectCommandInformation();
            return command.formatToProtocolCommand();
        }

        System.out.println("Invalid command. Type 'help' for a list of available commands.");
        return null;
    }
}