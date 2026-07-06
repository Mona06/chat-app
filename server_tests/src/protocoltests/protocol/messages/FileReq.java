package protocoltests.protocol.messages;

public record FileReq(String sender, String filename, long size) {}
