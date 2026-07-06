package custom_server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
            final Server server = Server.getInstance();
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
