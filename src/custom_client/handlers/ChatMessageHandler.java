package custom_client.handlers;


import com.fasterxml.jackson.databind.JsonNode;
import custom_client.Client;
import custom_client.IncomingServerResponse;
import custom_client.LineHandler;

/**
 * This class is responsible for handling the general chat messages from the server.
 */
public class ChatMessageHandler implements LineHandler {

    public ChatMessageHandler(Client client) {
        super();
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
            case HI:
                if (json.has("version")) {
                    System.out.println("Connected to server version " + json.get("version").asText());
                } else {
                    System.out.println("Ready");
                }
                break;
            case LOGON_RESP:
                System.out.println("You have entered the chat room");
                break;
            case JOINED:
                System.out.println(json.get("username").asText() + " joined the chat room");
                break;
            case BROADCAST_RESP:
                System.out.println("You have broadcasted a message");
                break;
            case BROADCAST:
                System.out.println(json.get("username").asText() + ": " + json.get("message").asText());
                break;
            case CL_RESP:
                if (json.has("users") && json.get("users").isArray()) {
                    System.out.println("Connected clients:");
                    for (JsonNode user : json.get("users")) {
                        if (user.isTextual() && !user.asText().trim().isEmpty()) {
                            System.out.println("- " + user.asText().trim());
                        }
                    }
                }
                break;
            case PM_RESP:
                System.out.println("You have sent a private message");
                break;
            case PM:
                System.out.println(json.get("username").asText() + " sent you a private message: " + json.get("message").asText());
                break;
            case BYE_RESP:
                System.out.println("You have left the chat room");
                break;
            case LEFT:
                System.out.println(json.get("username").asText() + " left the chat room");
                break;
            case UNKNOWN_COMMAND:
                System.out.println("Unknown command");
                break;
            case PARSE_ERROR:
                System.out.println("Parse error");
                break;
            default:
                System.out.println("ChatMessageHandler is ignoring " + response.responseType.name());
        }
    }
}
