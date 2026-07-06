package protocoltests.protocol.messages;

public record FileComplete(String sender, String filename, String checksum) {}
