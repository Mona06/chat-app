package custom_client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class is responsible for dispatching the input messages from the server.
 * This class exists because BufferedReaders in java consume the input, and with the desire for a separate thread
 * for the heartbeat we had to dispatch to all the input reading threads.
 */
public class InputDispatcher extends Thread {
    private final BufferedReader reader;

    private final Map<String, MessageQueue> messageQueues = new HashMap<>();

    public static final String HEARTBEAT_MESSAGES = "heartbeat";

    public static final String GAME_MESSAGES = "game";

    public static final String SERVER_MESSAGES = "server";

    public static final String FILE_MESSAGES = "file";


    public InputDispatcher(Client nodeClient) throws IOException {
        this.reader = new BufferedReader(new InputStreamReader(nodeClient.getInputStream()));

        messageQueues.put(HEARTBEAT_MESSAGES, new MessageQueue());
        messageQueues.put(GAME_MESSAGES, new MessageQueue());
        messageQueues.put(SERVER_MESSAGES, new MessageQueue());
        messageQueues.put(FILE_MESSAGES, new MessageQueue());
    }

    /**
     * Registers a handler for a specific message type
     */
    public void addHandler(String messageType, LineHandler handler) {
        MessageQueue queue = messageQueues.get(messageType);
        if (queue != null) {
            queue.setHandler(handler);
        }
    }

    /**
     * Analyzes message type and route to appropriate queue
     */
    private void broadcast(String message) {
        if (message.equals("PING")) {
            messageQueues.get(HEARTBEAT_MESSAGES).enqueue(message);
        } else if (message.startsWith("GAME_") || message.startsWith("INIT_GAME_")) {
            messageQueues.get(GAME_MESSAGES).enqueue(message);
        } else if (message.startsWith("FILE_") || message.startsWith("INIT_FILE_")) {
            messageQueues.get(FILE_MESSAGES).enqueue(message);
        } else {
            messageQueues.get(SERVER_MESSAGES).enqueue(message);
        }
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                broadcast(line);
            }
        } catch (IOException e) {
            System.err.println("Error while reading from input stream " + e.getMessage());
        }
    }

    /**
     * Represents a queue for a specific message type with its handler
     */
    private static class MessageQueue {
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        private LineHandler handler;

        private Thread processingThread;

        public void setHandler(LineHandler handler) {
            this.handler = handler;
            startProcessing();
        }

        public void enqueue(String message) {
            try {
                queue.put(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void startProcessing() {
            if (processingThread != null) {
                return;
            }

            processingThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String message = queue.take();
                        if (handler != null) {
                            handler.handleLine(message);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            processingThread.setDaemon(true);
            processingThread.start();
        }
    }
}