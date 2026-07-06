package custom_client;

public enum ServerResponseType {
    HI,
    LOGON_RESP,
    JOINED,
    BROADCAST_RESP,
    BROADCAST,
    CL_RESP,
    PM_RESP,
    INIT_GAME_RESP,
    GAME_REQ,
    GAME_INV_RESP,
    GAME_START,
    GAME_ROUND_RESULT,
    GAME_END,
    GAME_UPDATE,
    GAME_MOVE_RESP,
    GAME_CHOICE_RESP,
    GAME_UPDATE_RESP,
    INIT_FILE_RESP,
    FILE_REQ,
    FILE_INV_RESP,
    FILE_INV_REPLY,
    FILE_COMPLETE,
    FILE_DELIVERED,
    FILE_READY,
    PM,
    PING,
    PONG_ERROR,
    HANGUP,
    BYE_RESP,
    LEFT,
    UNKNOWN_COMMAND,
    PARSE_ERROR;

    public static ServerResponseType fromString(String name) throws Exception {
        for (ServerResponseType responseType : ServerResponseType.values()) {
            if (responseType.name().equalsIgnoreCase(name)) {
                return responseType;
            }
        }
        throw new Exception(name);
    }
}
