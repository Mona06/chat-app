package protocoltests.protocol.messages;

import java.util.Map;

public record GameEnd(int result, String winner, String loser,
                      Map<String, Integer> finalScores) {}
