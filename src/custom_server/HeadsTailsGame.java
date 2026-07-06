package custom_server;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Heads or Tails game between two players.
 * The game continues until one player reaches 3 points.
 */
public class HeadsTailsGame {
    private static final int WINNING_SCORE = 3;
    private static final SecureRandom random = new SecureRandom();

    private final String player1;
    private final String player2;
    private final Map<String, Integer> scores;
    private final Map<String, Integer> choices; // Current round choices (0=heads, 1=tails)
    private int currentRound;
    private boolean gameEnded;

    public enum CoinSide {
        HEADS(0), TAILS(1);

        private final int code;

        CoinSide(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static CoinSide fromCode(int code) {
            return code == 0 ? HEADS : TAILS;
        }

        public static CoinSide flip() {
            return random.nextBoolean() ? HEADS : TAILS;
        }
    }

    public HeadsTailsGame(String player1, String player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.scores = new HashMap<>();
        this.choices = new HashMap<>();
        this.scores.put(player1, 0);
        this.scores.put(player2, 0);
        this.currentRound = 0;
        this.gameEnded = false;
    }


    public void startGame() {
        this.currentRound = 1;
    }

    /**
     * Submits a player's choice for the current round
     *
     * @param username the player's username
     * @param choice   the numeric choice (0 for heads, 1 for tails)
     * @return true if the choice was recorded, false if already submitted
     */
    public boolean submitChoice(String username, int choice) {
        if (gameEnded) {
            return false;
        }

        if (!isValidPlayer(username)) {
            return false;
        }

        if (choices.containsKey(username)) {
            return false; // Already submitted
        }

        choices.put(username, choice);
        return true;
    }

    /**
     * Checks if both players have submitted their choices
     */
    public boolean bothPlayersChose() {
        return choices.size() == 2;
    }

    /**
     * Processes the round and determine the outcome
     *
     * @return RoundResult containing all information about the round outcome
     */
    public RoundResult processRound() {
        if (!bothPlayersChose()) {
            throw new IllegalStateException("Both players must submit choices before processing round");
        }

        int choice1 = choices.get(player1);
        int choice2 = choices.get(player2);

        CoinSide side1 = CoinSide.fromCode(choice1);
        CoinSide side2 = CoinSide.fromCode(choice2);

        RoundResult result = new RoundResult();
        result.round = currentRound;
        result.player1Choice = choice1;
        result.player2Choice = choice2;

        // Checking if both chose the same side
        if (side1 == side2) {
            result.outcome = RoundOutcome.DRAW;
            result.coinResult = CoinSide.flip().getCode(); // Flip anyway
        } else {
            // Players chose different sides, flip the coin
            CoinSide coinFlip = CoinSide.flip();
            result.coinResult = coinFlip.getCode();

            // Determining the winner
            if (side1 == coinFlip) {
                result.outcome = RoundOutcome.PLAYER1_WINS;
                result.roundWinner = player1;
                scores.put(player1, scores.get(player1) + 1);
            } else {
                result.outcome = RoundOutcome.PLAYER2_WINS;
                result.roundWinner = player2;
                scores.put(player2, scores.get(player2) + 1);
            }
        }

        result.scores = new HashMap<>(scores);

        // Checking if game is over
        if (scores.get(player1) >= WINNING_SCORE || scores.get(player2) >= WINNING_SCORE) {
            gameEnded = true;
        } else {
            currentRound++;
            choices.clear();
        }

        return result;
    }

    /**
     * Checks if the game has a winner
     */
    public boolean hasWinner() {
        return scores.get(player1) >= WINNING_SCORE || scores.get(player2) >= WINNING_SCORE;
    }


    public GameResult getGameResult() {
        if (!hasWinner()) {
            return null;
        }

        String winner = scores.get(player1) >= WINNING_SCORE ? player1 : player2;
        String loser = winner.equals(player1) ? player2 : player1;

        return new GameResult(winner, loser, scores);
    }

    /**
     * Checks if a player has already submitted their choice for this round
     */
    public boolean hasPlayerChosen(String username) {
        return choices.containsKey(username);
    }

    /**
     * Checks if username is a valid player in this game
     */
    public boolean isValidPlayer(String username) {
        return username.equals(player1) || username.equals(player2);
    }

    /**
     * Gets the opponent of a given player
     */
    public String getOpponent(String username) {
        if (username.equals(player1)) {
            return player2;
        } else if (username.equals(player2)) {
            return player1;
        }
        return null;
    }

    public String getPlayer1() {
        return player1;
    }

    public String getPlayer2() {
        return player2;
    }

    public Map<String, Integer> getScores() {
        return new HashMap<>(scores);
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    /**
     * Result of a single round
     */
    public static class RoundResult {
        public int round;
        public int coinResult; // 0=heads, 1=tails
        public int player1Choice; // 0=heads, 1=tails
        public int player2Choice;
        public RoundOutcome outcome;
        public String roundWinner; // null if draw
        public Map<String, Integer> scores;

        public RoundResult() {
        }
    }

    /**
     * Possible outcomes for a round
     */
    public enum RoundOutcome {
        DRAW,
        PLAYER1_WINS,
        PLAYER2_WINS
    }
}