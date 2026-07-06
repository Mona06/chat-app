package protocoltests.protocol.messages;

import java.util.Map;

public record GameStart(int round, Map<String, Integer> scores) {}
