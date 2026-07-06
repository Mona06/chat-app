package custom_server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Server {
    private static final String VERSION = "1.7.0";
    private static final int SERVER_PORT = 8080;
    private static final Server INSTANCE;

    private final ServerSocket serverSocket;
    private final List<ClientConnection> unregisteredConnections;
    private final ConcurrentHashMap<String, ClientConnection> registeredConnections;
    public final FileTransferServer transferServer = new FileTransferServer();

    private Server() throws IOException {
        serverSocket = new ServerSocket(SERVER_PORT);
        unregisteredConnections = Collections.synchronizedList(new ArrayList<>());
        registeredConnections = new ConcurrentHashMap<>();
    }

    public void start() throws IOException {
        System.out.println("Server started on port " + SERVER_PORT);
        while (true) {
            Socket socket = serverSocket.accept();
            ClientConnection connection = new ClientConnection(socket, this);
            unregisteredConnections.add(connection);
            connection.sendMessage("HI {\"version\":\"" + VERSION + "\"}");
            connection.start();
        }
    }

    static {
        try {
            INSTANCE = new Server();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Server getInstance() {
        return INSTANCE;
    }

    public synchronized void registerClient(String username, ClientConnection connection) {
        if (!username.matches("^[a-zA-Z0-9_]{3,14}$")) {
            connection.sendMessage("LOGON_RESP {\"status\":\"ERROR\", \"code\":5001}");
            return;
        }

        if (registeredConnections.containsKey(username)) {
            connection.sendMessage("LOGON_RESP {\"status\":\"ERROR\", \"code\":5000}");
            return;
        }

        if (connection.getUsername() != null) {
            connection.sendMessage("LOGON_RESP {\"status\":\"ERROR\", \"code\":5002}");
            return;
        }

        unregisteredConnections.remove(connection);
        registeredConnections.put(username, connection);
        connection.setUsername(username);

        connection.sendMessage("LOGON_RESP {\"status\":\"OK\"}");
        connection.startHeartbeat();

        broadcastUserJoined(username);
    }

    public ClientConnection getClientConnection(String username) {
        return registeredConnections.get(username);
    }

    public void handleClientListRequest(ClientConnection requesterConnection) {
        if (requesterConnection.getUsername() == null) {
            requesterConnection.sendMessage("CL_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        String usersJson = registeredConnections.keySet().stream()
                .map(username -> "\"" + username + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        requesterConnection.sendMessage("CL_RESP {\"status\":\"OK\", \"users\":" + usersJson + "}");
    }

    public void sendPrivateMessage(String sender, String receiver, String message) {
        ClientConnection receiverConn = registeredConnections.get(receiver);
        ClientConnection senderConn = registeredConnections.get(sender);

        if (senderConn != null) {
            senderConn.sendMessage("PM_RESP {\"status\":\"OK\"}");
        }

        String privateMsg = "PM {\"username\":\"" + sender + "\", \"message\":\"" + message + "\"}";
        receiverConn.sendMessage(privateMsg);
    }

    public void broadcastMessage(String sender, String message) {
        ClientConnection senderConn = registeredConnections.get(sender);
        if (senderConn == null) {
            return;
        }

        senderConn.sendMessage("BROADCAST_RESP {\"status\":\"OK\"}");

        String broadcastMsg = "BROADCAST {\"username\":\"" + sender + "\",\"message\":\"" + message + "\"}";
        for (ClientConnection conn : registeredConnections.values()) {
            if (!conn.getUsername().equals(sender)) {
                conn.sendMessage(broadcastMsg);
            }
        }
    }


    public void handleClientLeave(String username) {
        ClientConnection connection = registeredConnections.remove(username);
        if (connection != null) {
            // Cleaning up any games this player was involved in
            GameStateManager.getINSTANCE().cleanupPlayerGames(username);

            connection.sendMessage("BYE_RESP {\"status\":\"OK\"}");
            String leftMessage = "LEFT {\"username\":\"" + username + "\"}";
            for (ClientConnection conn : registeredConnections.values()) {
                conn.sendMessage(leftMessage);
            }
        }
    }

    /**
     * Notify other clients that a user left (without sending BYE_RESP)
     * Used when BYE_RESP was already sent by the connection
     */
    public void notifyClientLeft(String username) {
        registeredConnections.remove(username);
        // Cleaning up any games this player was involved in
        GameStateManager.getINSTANCE().cleanupPlayerGames(username);

        // Notifying other clients
        String leftMessage = "LEFT {\"username\":\"" + username + "\"}";
        for (ClientConnection conn : registeredConnections.values()) {
            conn.sendMessage(leftMessage);
        }
    }

    private void broadcastUserJoined(String username) {
        String joinMessage = "JOINED {\"username\":\"" + username + "\"}";
        for (ClientConnection conn : registeredConnections.values()) {
            if (!conn.getUsername().equals(username)) {
                conn.sendMessage(joinMessage);
            }
        }
    }
}