package protocoltests.protocol.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import protocoltests.protocol.messages.*;

import java.util.HashMap;
import java.util.Map;

public class Utils {

    private final static ObjectMapper mapper = new ObjectMapper();
    private final static Map<Class<?>, String> objToNameMapping = new HashMap<>();
    static {
        objToNameMapping.put(Logon.class, "LOGON");
        objToNameMapping.put(LogonResp.class, "LOGON_RESP");
        objToNameMapping.put(BroadcastReq.class, "BROADCAST_REQ");
        objToNameMapping.put(BroadcastResp.class, "BROADCAST_RESP");
        objToNameMapping.put(Broadcast.class, "BROADCAST");
        objToNameMapping.put(Joined.class, "JOINED");
        objToNameMapping.put(ParseError.class, "PARSE_ERROR");
        objToNameMapping.put(Pong.class, "PONG");
        objToNameMapping.put(PongError.class, "PONG_ERROR");
        objToNameMapping.put(Hi.class, "HI");
        objToNameMapping.put(Ping.class, "PING");
        objToNameMapping.put(ByeResp.class, "BYE_RESP");
        objToNameMapping.put(ClientListReq.class, "CL_REQ");
        objToNameMapping.put(ClientListResp.class, "CL_RESP");

        // Private Messages
        objToNameMapping.put(PmReq.class, "PM_REQ");
        objToNameMapping.put(PmResp.class, "PM_RESP");
        objToNameMapping.put(Pm.class, "PM");

        // Game messages
        objToNameMapping.put(InitGameReq.class, "INIT_GAME_REQ");
        objToNameMapping.put(InitGameResp.class, "INIT_GAME_RESP");
        objToNameMapping.put(GameReq.class, "GAME_REQ");
        objToNameMapping.put(GameInvReq.class, "GAME_INV_REQ");
        objToNameMapping.put(GameInvResp.class, "GAME_INV_RESP");
        objToNameMapping.put(GameStart.class, "GAME_START");
        objToNameMapping.put(GameChoiceReq.class, "GAME_CHOICE_REQ");
        objToNameMapping.put(GameChoiceResp.class, "GAME_CHOICE_RESP");
        objToNameMapping.put(GameRoundResult.class, "GAME_ROUND_RESULT");
        objToNameMapping.put(GameEnd.class, "GAME_END");

        // File transfer messages
        objToNameMapping.put(InitFileReq.class, "INIT_FILE_REQ");
        objToNameMapping.put(InitFileResp.class, "INIT_FILE_RESP");
        objToNameMapping.put(FileReq.class, "FILE_REQ");
        objToNameMapping.put(FileInvReq.class, "FILE_INV_REQ");
        objToNameMapping.put(FileInvResp.class, "FILE_INV_RESP");
        objToNameMapping.put(FileComplete.class, "FILE_COMPLETE");
        objToNameMapping.put(FileDelivered.class, "FILE_DELIVERED");
    }

    public static String objectToMessage(Object object) throws JsonProcessingException {
        Class<?> clazz = object.getClass();
        String header = objToNameMapping.get(clazz);
        if (header == null) {
            throw new RuntimeException("Cannot convert this class to a message");
        }
        String body = mapper.writeValueAsString(object);
        return header + " " + body;
    }

    public static <T> T messageToObject(String message) throws JsonProcessingException {
        String[] parts = message.split(" ", 2);
        if (parts.length > 2 || parts.length == 0) {
            throw new RuntimeException("Invalid message");
        }
        String header = parts[0];
        String body = "{}";
        if (parts.length == 2) {
            body = parts[1];
        }
        Class<?> clazz = getClass(header);
        Object obj = mapper.readValue(body, clazz);
        return (T) clazz.cast(obj);
    }

    private static Class<?> getClass(String header) {
        return objToNameMapping.entrySet().stream()
                .filter(e -> e.getValue().equals(header))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cannot find class belonging to header " + header));
    }
}
