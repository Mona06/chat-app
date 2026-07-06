package protocoltests.protocol.messages;

import java.util.Map;

public record GameRoundResult(int round, int coinResult, int yourChoice,
                               int opponentChoice, int outcome, String roundWinner,
                               Map<String, Integer> scores) {}
