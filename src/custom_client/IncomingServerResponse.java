package custom_client;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class IncomingServerResponse {

    public ServerResponseType responseType;

    public final ServerErrorCode errorCode;

    public final JsonNode jsonNode;

    public IncomingServerResponse(String line) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String[] parts = line.split(" ", 2);
            String command = parts[0];
            String jsonStr = parts.length > 1 ? parts[1] : "{}";

            this.jsonNode = objectMapper.readTree(jsonStr);

            try {
                this.responseType = ServerResponseType.fromString(command);
            } catch (Exception e) {
                System.out.println("Unknown command: " + command);
                this.responseType = ServerResponseType.UNKNOWN_COMMAND;
            }

            this.errorCode = extractErrorCode(jsonNode);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing server response: " + e.getMessage(), e);
        }
    }

    public boolean containsErrorCode() {
        return errorCode != null;
    }

    public ServerResponseType getResponseType() {
        return responseType;
    }

    public ServerErrorCode getErrorCode() {
        return errorCode;
    }

    public JsonNode getJsonNode() {
        return jsonNode;
    }


    /**
     * Method to extract and handle error codes
     *
     * @param jsonNode The JSON node to check for error codes
     * @return ServerErrorCode if found, null otherwise
     */
    public static ServerErrorCode extractErrorCode(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }

        if (jsonNode.has("code")) {
            String code = jsonNode.get("code").asText();
            return ServerErrorCode.fromString(code);
        }


        if (jsonNode.has("status") && "ERROR".equals(jsonNode.get("status").asText())) {
            return ServerErrorCode.GENERIC_ERROR;
        }

        return null;
    }

    /**
     * Method to handle error codes with consistent logging
     *
     * @param response The error code to handle
     */
    public static void handleError(IncomingServerResponse response) {
        if (response.errorCode == null) {
            return;
        }

        switch (response.errorCode) {
            case USER_ALREADY_EXISTS:
                System.out.println("User already exists");
                break;
            case INVALID_USERNAME_FORMAT:
                System.out.println("Invalid username format");
                break;
            case ALREADY_LOGGED_IN:
                System.out.println("Already logged in");
                break;
            case USER_NOT_LOGGED_IN:
                System.out.println("User not logged in");
                break;
            case NO_PONG_RECEIVED:
                System.out.println("No pong received");
                break;
            case PONG_WITHOUT_PING:
                System.out.println("Pong without ping");
                break;
            case INVALID_USER:
                System.out.println("User does not exist");
                break;
            case ANOTHER_GAME_RUNNING:
                System.out.println("Another game is already running between " +
                        response.jsonNode.get("player1").asText() + " and " +
                        response.jsonNode.get("player2").asText());
                break;
            case INVALID_CHOICE:
                System.out.println("Invalid choice. Please choose 'heads' or 'tails'");
                break;
            case CHOICE_ALREADY_SUBMITTED:
                System.out.println("You have already submitted your choice for this round");
                break;
            case NO_GAME_IN_PROGRESS:
                System.out.println("No game running at the moment");
                break;
            case GENERIC_ERROR:
                System.out.println("An unknown error occurred");
                break;
            default:
                System.out.println("Unhandled error code - " + response);
        }
    }
}
