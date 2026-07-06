package custom_server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

public class ClientConnection {
    private final Socket socket;
    private final Server server;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private String username;
    private final Thread inputThread;
    private Timer heartbeatTimer;
    private boolean awaitingPong;
    private int missedPongs;

    public ClientConnection(Socket socket, Server server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.inputThread = new Thread(this::handleInput);
        this.awaitingPong = false;
        this.missedPongs = 0;
    }

    public void start() {
        inputThread.start();
    }

    public void startHeartbeat() {
        if (heartbeatTimer != null) {
            return;
        }
        heartbeatTimer = new Timer();
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (awaitingPong) {
                    missedPongs++;
                    if (missedPongs >= 3) {
                        handleDisconnect("HANGUP {\"reason\":7000}");
                        return;
                    }
                }
                sendMessage("PING");
                awaitingPong = true;
            }
        }, 10000, 10000); // 10 seconds interval
    }

    private void handleInput() {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                line = line.replaceAll("\\r", "");

//                System.out.println("[DEBUG] Received complete line: [" + line + "]");
                try {
                    processMessage(line);
                } catch (Exception e) {
                    System.out.println("[DEBUG] Exception in processMessage: " + e.getMessage());
                    sendMessage("PARSE_ERROR");
                }
            }
        } catch (IOException e) {
            System.out.println("[DEBUG] IOException in handleInput for user " + username + ": " + e.getMessage());
        } finally {
            System.out.println("[DEBUG] Input loop for user " + username + " finished. Calling handleDisconnect.");
            handleDisconnect(null);
        }
    }

    private void processMessage(String message) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String[] parts = message.split(" ", 2);
            String command = parts[0];
            String jsonStr = "{}";
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                jsonStr = parts[1].trim();
            }

            try {
                JsonNode json = objectMapper.readTree(jsonStr);

                switch (command) {
                    case "LOGON":
                        handleLogon(json);
                        break;
                    case "BROADCAST_REQ":
                        handleBroadcast(json);
                        break;
                    case "CL_REQ":
                        server.handleClientListRequest(this);
                        break;
                    case "PM_REQ":
                        handlePM(json);
                        break;
                    case "INIT_GAME_REQ":
                        handleInitGame(json);
                        break;
                    case "GAME_INV_REQ":
                        handleGameInvResponse(json);
                        break;
                    case "GAME_CHOICE_REQ":
                        handleGameChoice(json);
                        break;
                    case "INIT_FILE_REQ":
                        handleFileInit(json);
                        break;
                    case "FILE_INV_REQ":
                        handleFileInvResponse(json);
                        break;
                    case "BYE":
                        handleBye();
                        break;
                    case "PONG":
                        handlePong();
                        break;
                    default:
                        sendMessage("UNKNOWN_COMMAND");
                }
            } catch (IOException e) {
                sendMessage("PARSE_ERROR");
            }
        } catch (Exception e) {
            sendMessage("PARSE_ERROR");
        }
    }


    private void handleFileInvResponse(JsonNode json) {
        if (username == null) {
            sendMessage("FILE_INV_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        if (!json.has("username") || !json.has("accept")) {
            sendMessage("PARSE_ERROR");
            return;
        }

        String sender = json.get("username").asText();
        boolean accepted = json.get("accept").asBoolean();

        if (accepted) {
            // Allocating port for receiver to download
            int downloadPort = server.transferServer.getAvailablePort();

            // Preparing download session (but don't open socket yet - wait for upload to complete)
            server.transferServer.prepareDownloadSession(sender, username, downloadPort);

            // Notifying sender that receiver accepted
            server.getClientConnection(sender).sendMessage("FILE_INV_REPLY {\"accepted\": true}");

            // Opening file socket for sender to upload
            server.transferServer.openFileSocket(sender);

            // Sending port to receiver for downloading
            sendMessage("FILE_INV_RESP {\"status\": \"OK\", \"port\": " + downloadPort + "}");
        } else {
            server.getClientConnection(sender).sendMessage("FILE_INV_REPLY {\"accepted\": false}");
            sendMessage("FILE_INV_RESP {\"status\": \"OK\"}");
        }
    }

    private void handleFileInit(JsonNode json) {
        if (username == null) {
            sendMessage("INIT_FILE_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        if (!json.has("receiver")) {
            sendMessage("PARSE_ERROR");
            return;
        }

        String receiver = json.get("receiver").asText();
        String filename = json.get("filename").asText();
        long size = json.get("size").asLong();
        String checksum = json.get("checksum").asText();

        ClientConnection receiverConnection = server.getClientConnection(receiver);
        if (receiverConnection == null) {
            sendMessage("INIT_FILE_RESP {\"status\":\"ERROR\", \"code\":6001}");
            return;
        }

        server.transferServer.initiateFileTransfer(
                server.getClientConnection(username),
                server.getClientConnection(receiver),
                filename, size, checksum
        );
    }

    private void handleLogon(JsonNode json) {
        if (!json.has("username")) {
            sendMessage("LOGON_RESP {\"status\":\"ERROR\", \"code\":5001}");
            return;
        }
        server.registerClient(json.get("username").asText(), this);
    }

    private void handlePM(JsonNode json) {
        if (username == null) {
            sendMessage("PM_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        if (!json.has("receiver") || !json.has("message")) {
            sendMessage("PARSE_ERROR");
            return;
        }

        String receiver = json.get("receiver").asText();
        String message = json.get("message").asText();

        if (server.getClientConnection(receiver) == null) {
            sendMessage("PM_RESP {\"status\":\"ERROR\", \"code\":6001}");
            return;
        }

        if (message.isEmpty()) {
            sendMessage("PM_RESP {\"status\":\"ERROR\", \"code\":6002}");
            return;
        }

        server.sendPrivateMessage(username, receiver, message);
    }

    private void handleBroadcast(JsonNode json) {
        if (username == null) {
            sendMessage("BROADCAST_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        if (json.has("message")) {
            server.broadcastMessage(username, json.get("message").asText());
        } else {
            sendMessage("PARSE_ERROR");
        }
    }

    private void handleInitGame(JsonNode json) {
        if (username == null) {
            sendMessage("INIT_GAME_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        if (!json.has("receiver")) {
            sendMessage("PARSE_ERROR");
            return;
        }

        String receiver = json.get("receiver").asText();

        if (server.getClientConnection(receiver) == null) {
            sendMessage("INIT_GAME_RESP {\"status\":\"ERROR\", \"code\":6001}");
            return;
        }

        HeadsTailsGame currentGame = GameStateManager.getINSTANCE().getCurrentGame();
        if (currentGame != null && !currentGame.isGameEnded()) {
            sendMessage(String.format(
                    "INIT_GAME_RESP {\"status\":\"ERROR\", \"code\":9000, \"player1\":\"%s\", \"player2\":\"%s\"}",
                    currentGame.getPlayer1(),
                    currentGame.getPlayer2()
            ));
            return;
        }

        // Initiating game through game manager
        boolean success = GameStateManager.getINSTANCE().initiateGame(username, receiver);
        if (!success) {
            String[] players = GameStateManager.getINSTANCE().getActiveGamePlayers();
            if (players != null) {
                sendMessage(String.format(
                        "INIT_GAME_RESP {\"status\":\"ERROR\", \"code\":9000, \"player1\":\"%s\", \"player2\":\"%s\"}",
                        players[0], players[1]
                ));
            } else {
                sendMessage("INIT_GAME_RESP {\"status\":\"ERROR\", \"code\":9000}");
            }
            return;
        }

        // Sending invitation to receiver
        sendMessage("INIT_GAME_RESP {\"status\":\"OK\"}");
        ClientConnection receiverConn = server.getClientConnection(receiver);
        if (receiverConn != null) {
            receiverConn.sendMessage(String.format("GAME_REQ {\"username\":\"%s\"}", username));
        }
    }

    private void handleGameInvResponse(JsonNode json) {
        if (username == null) {
            sendMessage("GAME_INV_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        if (!json.has("username") || !json.has("accept")) {
            sendMessage("PARSE_ERROR");
            return;
        }

        String challenger = json.get("username").asText();
        boolean accepted = json.get("accept").asBoolean();

        // Getting the pending invitation
        HeadsTailsGame pendingGame = GameStateManager.getINSTANCE().getPendingInvitation(challenger);

        if (pendingGame == null) {
            sendMessage("GAME_INV_RESP {\"status\":\"ERROR\", \"code\":9001}");
            return;
        }

        // Sending OK response to the person who accepted/declined
        sendMessage("GAME_INV_RESP {\"status\":\"OK\"}");

        // Notifying the challenger of the response (with username and accept fields)
        ClientConnection challengerConn = server.getClientConnection(challenger);
        if (challengerConn != null) {
            challengerConn.sendMessage(String.format(
                    "GAME_INV_RESP {\"username\":\"%s\", \"accept\":%b}",
                    username, accepted
            ));
        }

        if (accepted) {
            // Accepting the game
            HeadsTailsGame game = GameStateManager.getINSTANCE().acceptGame(challenger, username);

            if (game != null) {
                // Sending GAME_START to both players
                String gameStartMsg = String.format(
                        "GAME_START {\"round\":%d, \"scores\":{\"%s\":0, \"%s\":0}}",
                        game.getCurrentRound(),
                        game.getPlayer1(),
                        game.getPlayer2()
                );

                if (challengerConn != null) {
                    challengerConn.sendMessage(gameStartMsg);
                }
                sendMessage(gameStartMsg);
            }
        } else {
            // Declining the game
            GameStateManager.getINSTANCE().declineGame(challenger);
        }
    }

    /**
     * Handles player's choice submission
     */
    private void handleGameChoice(JsonNode json) {
        if (username == null) {
            sendMessage("GAME_CHOICE_RESP {\"status\":\"ERROR\", \"code\":6000}");
            return;
        }

        if (!json.has("choice")) {
            sendMessage("PARSE_ERROR");
            return;
        }

        int choice = json.get("choice").asInt();

        // Validate choice
        if (choice != 0 && choice != 1) {
            sendMessage("GAME_CHOICE_RESP {\"status\":\"ERROR\", \"code\":9002}");
            return;
        }

        // Getting current game
        HeadsTailsGame game = GameStateManager.getINSTANCE().getGameForPlayer(username);

        if (game == null) {
            sendMessage("GAME_CHOICE_RESP {\"status\":\"ERROR\", \"code\":9001}");
            return;
        }

        // Checking if player already submitted choice
        if (game.hasPlayerChosen(username)) {
            sendMessage("GAME_CHOICE_RESP {\"status\":\"ERROR\", \"code\":9003}");
            return;
        }

        // Submitting choice
        boolean success = game.submitChoice(username, choice);

        if (!success) {
            sendMessage("GAME_CHOICE_RESP {\"status\":\"ERROR\", \"code\":9001}");
            return;
        }

        // Sending confirmation
        sendMessage("GAME_CHOICE_RESP {\"status\":\"OK\"}");

        // Checking if both players have chosen
        if (game.bothPlayersChose()) {
            // Processing the round
            HeadsTailsGame.RoundResult result = game.processRound();
            sendRoundResultToPlayers(game, result);

            // Checking if game is over
            if (game.hasWinner()) {
                GameResult gameResult = game.getGameResult();
                sendGameEndToPlayers(game, gameResult);
                GameStateManager.getINSTANCE().endGame();
            }
        }
    }

    /**
     * Sends round result to both players
     */
    private void sendRoundResultToPlayers(HeadsTailsGame game, HeadsTailsGame.RoundResult result) {
        String player1 = game.getPlayer1();
        String player2 = game.getPlayer2();

        ClientConnection player1Conn = server.getClientConnection(player1);
        ClientConnection player2Conn = server.getClientConnection(player2);

        // Building the scores
        StringBuilder scoresJson = new StringBuilder("{");
        boolean first = true;
        for (var entry : result.scores.entrySet()) {
            if (!first) scoresJson.append(",");
            scoresJson.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        scoresJson.append("}");

        // Send to player 1
        if (player1Conn != null) {
            int outcome1;
            if (result.outcome == HeadsTailsGame.RoundOutcome.DRAW) {
                outcome1 = 0;
            } else if (result.outcome == HeadsTailsGame.RoundOutcome.PLAYER1_WINS) {
                outcome1 = 1;
            } else {
                outcome1 = 2;
            }

            String roundWinnerJson = (result.roundWinner != null)
                    ? ", \"roundWinner\":\"" + result.roundWinner + "\""
                    : "";

            player1Conn.sendMessage(String.format(
                    "GAME_ROUND_RESULT {\"round\":%d, \"coinResult\":%d, \"yourChoice\":%d, \"opponentChoice\":%d, \"outcome\":%d%s, \"scores\":%s}",
                    result.round,
                    result.coinResult,
                    result.player1Choice,
                    result.player2Choice,
                    outcome1,
                    roundWinnerJson,
                    scoresJson
            ));
        }

        // Send to player 2
        if (player2Conn != null) {
            int outcome2;
            if (result.outcome == HeadsTailsGame.RoundOutcome.DRAW) {
                outcome2 = 0;
            } else if (result.outcome == HeadsTailsGame.RoundOutcome.PLAYER2_WINS) {
                outcome2 = 1;
            } else {
                outcome2 = 2;
            }

            String roundWinnerJson = (result.roundWinner != null)
                    ? ", \"roundWinner\":\"" + result.roundWinner + "\""
                    : "";

            player2Conn.sendMessage(String.format(
                    "GAME_ROUND_RESULT {\"round\":%d, \"coinResult\":%d, \"yourChoice\":%d, \"opponentChoice\":%d, \"outcome\":%d%s, \"scores\":%s}",
                    result.round,
                    result.coinResult,
                    result.player2Choice,
                    result.player1Choice,
                    outcome2,
                    roundWinnerJson,
                    scoresJson
            ));
        }
    }

    /**
     * Sends game end message to both players
     */
    private void sendGameEndToPlayers(HeadsTailsGame game, GameResult gameResult) {
        String player1 = game.getPlayer1();
        String player2 = game.getPlayer2();

        ClientConnection player1Conn = server.getClientConnection(player1);
        ClientConnection player2Conn = server.getClientConnection(player2);

        // Building final scores
        StringBuilder scoresJson = new StringBuilder("{");
        boolean first = true;
        for (var entry : gameResult.getFinalScores().entrySet()) {
            if (!first) scoresJson.append(",");
            scoresJson.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        scoresJson.append("}");

        // Send to player 1
        if (player1Conn != null) {
            int result1 = gameResult.getResultForPlayer(player1);
            player1Conn.sendMessage(String.format(
                    "GAME_END {\"result\":%d, \"winner\":\"%s\", \"loser\":\"%s\", \"finalScores\":%s}",
                    result1,
                    gameResult.getWinner(),
                    gameResult.getLoser(),
                    scoresJson
            ));
        }

        // Send to player 2
        if (player2Conn != null) {
            int result2 = gameResult.getResultForPlayer(player2);
            player2Conn.sendMessage(String.format(
                    "GAME_END {\"result\":%d, \"winner\":\"%s\", \"loser\":\"%s\", \"finalScores\":%s}",
                    result2,
                    gameResult.getWinner(),
                    gameResult.getLoser(),
                    scoresJson
            ));
        }
    }


    private void handlePong() {
        if (!awaitingPong) {
            sendMessage("PONG_ERROR {\"code\":8000}");
            return;
        }
        awaitingPong = false;
        missedPongs = 0;
    }

    private void handleBye() {
        sendMessage("BYE_RESP {\"status\":\"OK\"}");

        if (username != null) {
            server.notifyClientLeft(username);
        }

        handleDisconnect(null);
    }

    public void sendMessage(String message) {
        writer.println(message);
        writer.flush();
    }

    private void handleDisconnect(String reason) {
        if (reason != null) {
            sendMessage(reason);
        }
        if (username != null) {
            // Cleaning up any games this player was in
            GameStateManager.getINSTANCE().cleanupPlayerGames(username);
            server.transferServer.cleanupClientTransfers(username);
            server.handleClientLeave(username);
        }
        try {
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
            }
            socket.close();
        } catch (IOException e) {
            System.out.println("[DEBUG] IOException while closing socket for user " + (username != null ? username : "[unregistered]") + ": " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean matches(Socket otherSocket) {
        return this.socket.getInetAddress().equals(otherSocket.getInetAddress()) &&
                this.socket.getPort() == otherSocket.getPort();
    }
}