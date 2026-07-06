package custom_server;

import java.util.Map;

/**
 * Represents the final result of a game
 */
public class GameResult {
    public enum ResultType {
        WIN,
        DRAW
    }

    private final String winner;
    private final String loser;
    private final Map<String, Integer> finalScores;
    private final ResultType resultType;

    public GameResult(String winner, String loser, Map<String, Integer> finalScores) {
        this.winner = winner;
        this.loser = loser;
        this.finalScores = finalScores;
        this.resultType = (winner == null) ? ResultType.DRAW : ResultType.WIN;
    }

    public String getWinner() {
        return winner;
    }

    public String getLoser() {
        return loser;
    }

    public Map<String, Integer> getFinalScores() {
        return finalScores;
    }

    public ResultType getResultType() {
        return resultType;
    }

    /**
     * Gets the result from a specific player's perspective
     *
     * @param username the player's username
     * @return 1 if they won, 2 if they lost
     */
    public int getResultForPlayer(String username) {
        return username.equals(winner) ? 1 : 2;
    }
}