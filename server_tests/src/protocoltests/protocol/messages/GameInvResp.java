package protocoltests.protocol.messages;

public record GameInvResp(String status, Integer code, String username, Boolean accept) {}
