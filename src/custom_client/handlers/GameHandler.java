package custom_client.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import custom_client.Client;
import custom_client.ClientGameStateManager;
import custom_client.IncomingServerResponse;
import custom_client.LineHandler;

/**
 * Handler for all game-related messages from the server
 */
public class GameHandler implements LineHandler {
    private final ClientGameStateManager gameManager;

    public GameHandler(Client client) {
        this.gameManager = ClientGameStateManager.getInstance();
    }

    @Override
    public void handleLine(String line) {
        final IncomingServerResponse response = new IncomingServerResponse(line);
        final JsonNode json = response.getJsonNode();

        if (response.errorCode != null) {
            IncomingServerResponse.handleError(response);
            return;
        }

        switch (response.responseType) {
            case INIT_GAME_RESP:
                handleInitGameResponse();
                break;

            case GAME_REQ:
                handleGameRequest(json);
                break;

            case GAME_INV_RESP:
                handleGameInviteResponse(json);
                break;

            case GAME_START:
                handleGameStart(json);
                break;

            case GAME_CHOICE_RESP:
                handleChoiceResponse();
                break;

            case GAME_ROUND_RESULT:
                handleRoundResult(json);
                break;

            case GAME_END:
                handleGameEnd(json);
                break;

            default:
                System.out.println("GameHandler received unknown message: " + response.responseType.name());
        }
    }

    private void handleInitGameResponse() {
        System.out.println("Game invitation sent! Waiting for opponent to respond...");
    }

    private void handleGameRequest(JsonNode json) {
        String challenger = json.get("username").asText();
        gameManager.handleGameInvite(challenger);
    }

    private void handleGameInviteResponse(JsonNode json) {
        // Checking if this is a status response (for the person accepting/declining)
        if (json.has("status")) {
            String status = json.get("status").asText();
            if (status.equals("OK")) {
                // This is confirmation that our response was sent
                // The actual game start will come in GAME_START message
                return;
            }
            // If error, it will be handled by handleGameError
            return;
        }

        // This is a response FROM the other player (for the person who sent invitation)
        if (json.has("username") && json.has("accept")) {
            String username = json.get("username").asText();
            boolean accepted = json.get("accept").asBoolean();
            gameManager.handleInviteResponse(username, accepted);
        }
    }

    private void handleGameStart(JsonNode json) {
        int round = json.get("round").asInt();
        JsonNode scoresNode = json.get("scores");

        // Determining the opponent by finding the other username in scores
        String opponent = null;
        String currentOpponent = gameManager.getOpponent();

        var it = scoresNode.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            // If we already know the opponent from the invitation, verify it's them
            if (name.equals(currentOpponent)) {
                opponent = currentOpponent;
                break;
            }
            // Otherwise, finding the name that's not us (we need to know our own username somehow)
            // For now, just using the known opponent
            if (currentOpponent != null) {
                opponent = currentOpponent;
                break;
            }
        }

        // Fallback: if we still don't have opponent, we use the first name in scores
        if (opponent == null && scoresNode.fieldNames().hasNext()) {
            opponent = scoresNode.fieldNames().next();
        }

        gameManager.startGame(opponent, round);
        displayScores(scoresNode);
    }

    private void handleChoiceResponse() {
        // Client-side localized message
        System.out.println("Choice submitted! Waiting for opponent...");
    }

    /**
     * Convert numeric choice code to display text
     *
     * @param code 0 for heads, 1 for tails
     * @return localized choice string
     */
    private String choiceCodeToText(int code) {
        return code == 0 ? "heads" : "tails";
    }

    private void handleRoundResult(JsonNode json) {
        int round = json.get("round").asInt();
        int coinResult = json.get("coinResult").asInt();
        int yourChoice = json.get("yourChoice").asInt();
        int opponentChoice = json.get("opponentChoice").asInt();
        int outcome = json.get("outcome").asInt();
        String roundWinner = json.has("roundWinner") ? json.get("roundWinner").asText() : null;

        // Converting codes to display text
        String coinResultText = choiceCodeToText(coinResult);
        String yourChoiceText = choiceCodeToText(yourChoice);
        String opponentChoiceText = choiceCodeToText(opponentChoice);

        // Displaying round result
        System.out.println("\n===========================================");
        System.out.println("ROUND " + round + " RESULT");
        System.out.println("===========================================");
        System.out.println("Coin flip result: " + coinResultText.toUpperCase());
        System.out.println("Your choice: " + yourChoiceText);
        System.out.println("Opponent's choice: " + opponentChoiceText);
        System.out.println();

        if (outcome == 0) {
            System.out.println("Both players chose " + yourChoiceText + "!");
            System.out.println("No points awarded. Play another round!");
        } else if (outcome == 1) {
            System.out.println("You guessed correctly and earned 1 point!");
        } else if (outcome == 2) {
            System.out.println(roundWinner + " guessed correctly and earned 1 point.");
        }
        System.out.println("===========================================\n");

        JsonNode scoresNode = json.get("scores");
        displayScores(scoresNode);

        // If it's a draw or game continues, start new round
        if (outcome == 0) {
            gameManager.startNewRound(round + 1);
        } else {
            // Checking if game continues (no one has 3 points yet)
            boolean gameContinues = true;
            var it = scoresNode.fields();
            while (it.hasNext()) {
                var entry = it.next();
                if (entry.getValue().asInt() >= 3) {
                    gameContinues = false;
                    break;
                }
            }

            if (gameContinues) {
                gameManager.startNewRound(round + 1);
            }
        }
    }

    private void handleGameEnd(JsonNode json) {
        int result = json.get("result").asInt();
        String winner = json.get("winner").asText();
        String loser = json.get("loser").asText();

        if (json.has("finalScores")) {
            System.out.println("\nFinal Scores:");
            displayScores(json.get("finalScores"));
        }

        System.out.println("\n===========================================");
        System.out.println("GAME OVER");
        System.out.println("===========================================");

        if (result == 1) {
            System.out.println("CONGRATULATIONS! YOU WON!");
        } else {
            System.out.println("Game Over. " + winner + " won the game.");
        }

        System.out.println("Final result: " + winner + " defeated " + loser);
        System.out.println("===========================================\n");

        gameManager.handleGameEnd();
    }

    private void displayScores(JsonNode scoresNode) {
        if (scoresNode != null && scoresNode.isObject()) {
            System.out.println("Current Scores:");
            var it = scoresNode.fields();
            if (it.hasNext()) {
                var entry1 = it.next();
                if (it.hasNext()) {
                    var entry2 = it.next();
                    System.out.println(entry1.getKey() + ": " + entry1.getValue().asInt() + " | " +
                            entry2.getKey() + ": " + entry2.getValue().asInt());
                }
            }
            System.out.println();
        }
    }
}