package custom_client;

public enum ServerErrorCode {

    USER_ALREADY_EXISTS("5000"),
    INVALID_USERNAME_FORMAT("5001"),
    ALREADY_LOGGED_IN("5002"),
    USER_NOT_LOGGED_IN("6000"),
    NO_PONG_RECEIVED("7000"),
    PONG_WITHOUT_PING("8000"),
    INVALID_USER("6001"),
    EMPTY_MESSAGE_BODY("6002"),
    ANOTHER_GAME_RUNNING("9000"),
    NO_GAME_IN_PROGRESS("9001"),
    INVALID_CHOICE("9002"),
    CHOICE_ALREADY_SUBMITTED("9003"),
    GENERIC_ERROR("9999");


    private final String code;

    ServerErrorCode(String code) {
        this.code = code;
    }

    public static ServerErrorCode fromString(String code) {
        for (ServerErrorCode errorCode : ServerErrorCode.values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return null;
    }

}
