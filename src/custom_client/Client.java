package custom_client;

import custom_client.handlers.ChatMessageHandler;
import custom_client.handlers.FileTransferHandler;
import custom_client.handlers.GameHandler;
import custom_client.handlers.HeartbeatHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Client {
    private final Socket socket;

    private InputDispatcher dispatcher;

    private OutputThread outputThread;

    private boolean isRunning;

    /**
     * Creates a new client.
     * Required functionality:
     * - The user can log in with a username
     * - The user can broadcast messages to other logged-in users
     * - The user can log out
     * - A help menu can be shown
     * - Proper error handling when the connection fails or if any action is illegal
     * - Only one server can be connected to at a time
     * - The client pongs back to the server
     * - The client uses a CLI
     *
     * @throws IOException I/O exceptions occur when the socket is not connected.
     */
    public Client(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.isRunning = true;
    }

    public void run() throws IOException {
        dispatcher = new InputDispatcher(this);
        outputThread = new OutputThread(this);

        dispatcher.addHandler(InputDispatcher.HEARTBEAT_MESSAGES, new HeartbeatHandler(this));
        dispatcher.addHandler(InputDispatcher.GAME_MESSAGES, new GameHandler(this));
        dispatcher.addHandler(InputDispatcher.SERVER_MESSAGES, new ChatMessageHandler(this));
        dispatcher.addHandler(InputDispatcher.FILE_MESSAGES, new FileTransferHandler());

        dispatcher.start();
        outputThread.start();
    }

    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    public boolean isRunning() {
        return isRunning && !socket.isClosed();
    }
}