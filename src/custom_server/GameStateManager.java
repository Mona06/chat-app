package custom_server;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all active games on the server.
 * Ensures only one game can be active at a time.
 */
public class GameStateManager {
    private static GameStateManager INSTANCE;
    private HeadsTailsGame currentGame;
    private final Map<String, HeadsTailsGame> pendingInvitations; // challenger -> game

    private GameStateManager() {
        this.pendingInvitations = new HashMap<>();
    }

    public static GameStateManager getINSTANCE() {
        if (INSTANCE == null) {
            INSTANCE = new GameStateManager();
        }
        return INSTANCE;
    }

    /**
     * Initiates a new game invitation
     *
     * @return true if invitation was created, false if a game is already active
     */
    public synchronized boolean initiateGame(String challenger, String opponent) {
        // Checking if there's already an active game
        if (currentGame != null && !currentGame.isGameEnded()) {
            return false;
        }

        // Checking if there's already a pending invitation from this challenger
        if (pendingInvitations.containsKey(challenger)) {
            return false;
        }

        // Creating a pending game
        HeadsTailsGame game = new HeadsTailsGame(challenger, opponent);
        pendingInvitations.put(challenger, game);
        return true;
    }

    /**
     * Accepts a game invitation and start the game
     *
     * @return the game that was accepted, or null if no invitation exists
     */
    public synchronized HeadsTailsGame acceptGame(String challenger, String opponent) {
        HeadsTailsGame game = pendingInvitations.remove(challenger);
        if (game != null && game.getPlayer2().equals(opponent)) {
            game.startGame();
            currentGame = game;
            return game;
        }
        return null;
    }


    public synchronized void declineGame(String challenger) {
        pendingInvitations.remove(challenger);
    }

    public synchronized void endGame() {
        currentGame = null;
    }

    public HeadsTailsGame getCurrentGame() {
        return currentGame;
    }

    /**
     * Gets the game that a player is currently in
     */
    public synchronized HeadsTailsGame getGameForPlayer(String username) {
        if (currentGame != null && currentGame.isValidPlayer(username)) {
            return currentGame;
        }
        return null;
    }

    /**
     * Checks if a player is currently in an active game
     */
    public synchronized boolean isPlayerInGame(String username) {
        return currentGame != null && currentGame.isValidPlayer(username);
    }

    /**
     * Checks if there's a pending invitation for a challenger
     */
    public synchronized boolean hasPendingInvitation(String challenger) {
        return pendingInvitations.containsKey(challenger);
    }

    /**
     * Gets pending invitation for a challenger
     */
    public synchronized HeadsTailsGame getPendingInvitation(String challenger) {
        return pendingInvitations.get(challenger);
    }

    /**
     * Retrieves information about current active game players (for error messages)
     *
     * @return array with [player1, player2] or null if no active game
     */
    public synchronized String[] getActiveGamePlayers() {
        if (currentGame == null || currentGame.isGameEnded()) {
            return null;
        }
        return new String[]{currentGame.getPlayer1(), currentGame.getPlayer2()};
    }

    /**
     * Cleans up any games involving a disconnected player
     */
    public synchronized void cleanupPlayerGames(String username) {
        // Removing from active game
        if (currentGame != null && currentGame.isValidPlayer(username)) {
            currentGame = null;
        }

        // Removing pending invitations involving this player
        pendingInvitations.entrySet().removeIf(entry ->
                entry.getKey().equals(username) ||
                        entry.getValue().getPlayer2().equals(username)
        );
    }
}