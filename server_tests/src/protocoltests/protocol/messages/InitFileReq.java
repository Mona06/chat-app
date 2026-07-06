package protocoltests.protocol.messages;

public record InitFileReq(String receiver, String filename, long size, String checksum) {}
