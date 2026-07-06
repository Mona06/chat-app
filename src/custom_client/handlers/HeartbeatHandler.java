package custom_client.handlers;

import custom_client.Client;
import custom_client.LineHandler;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * This class is responsible for handling the heartbeat messages from the server.
 */
public class HeartbeatHandler implements LineHandler {
    final PrintWriter writer;

    public HeartbeatHandler(Client client) throws IOException {
        this.writer = new PrintWriter(client.getOutputStream(), true);
    }

    @Override
    public void handleLine(String line) {
        try {
            String[] parts = line.split(" ", 2);
            String command = parts[0];

            if (command.equals("PING")) {
                this.writer.println("PONG");
            } else {
                System.err.println("HeartbeatThread received non-PING command: " + command);
            }
        } catch (Exception e) {
            System.err.println("Error processing heartbeat: " + e.getMessage());
        }
    }

}
