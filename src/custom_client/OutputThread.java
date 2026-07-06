package custom_client;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class OutputThread extends Thread {
    private final Client client;

    private final PrintWriter writer;

    private final CommandExecutor commandExecutor;

    public OutputThread(Client client) throws IOException {
        this.client = client;
        this.writer = new PrintWriter(client.getOutputStream(), true);
        this.commandExecutor = new CommandExecutor();

    }


    @Override
    public void run() {
        final Scanner scanner = new Scanner(System.in);

        while (true) {
            try {
                String line = scanner.nextLine();
                if (line == null) {
                    break;
                }

                final String commandLine = commandExecutor.handleCommand(line);
                if (commandLine != null) {
                    this.writer.println(commandLine);
                }
            } catch (Exception e) {
                // Input error or connection lost
                System.out.println(e.getMessage());
                break;
            }
        }
    }
}
