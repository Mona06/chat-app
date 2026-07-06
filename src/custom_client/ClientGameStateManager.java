package custom_client;

import custom_client.commands.Command;
import custom_client.commands.GameChoiceCommand;
import custom_client.commands.GameResponseCommand;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the client-side state for the heads or tails game.
 * Singleton pattern ensures only one game state exists per client.
 */
public class ClientGameStateManager {
    private static volatile ClientGameStateManager INSTANCE;

    private final AtomicReference<String> pendingInviter;
    private final AtomicBoolean isInGame;
    private final AtomicBoolean waitingForChoice;
    private final AtomicBoolean choiceSubmitted;
    private final AtomicInteger currentRound;
    private final AtomicReference<String> opponent;
    private final Object lock = new Object();

    private ClientGameStateManager() {
        this.pendingInviter = new AtomicReference<>(null);
        this.isInGame = new AtomicBoolean(false);
        this.waitingForChoice = new AtomicBoolean(false);
        this.choiceSubmitted = new AtomicBoolean(false);
        this.currentRound = new AtomicInteger(0);
        this.opponent = new AtomicReference<>(null);
    }

    public static ClientGameStateManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ClientGameStateManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ClientGameStateManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Resets all game state to initial values
     */
    private void reset() {
        synchronized (lock) {
            pendingInviter.set(null);
            isInGame.set(false);
            waitingForChoice.set(false);
            choiceSubmitted.set(false);
            currentRound.set(0);
            opponent.set(null);
        }
    }

    /**
     * Handles incoming game invitation
     */
    public void handleGameInvite(String inviter) {
        pendingInviter.set(inviter);
        System.out.println("\n===========================================");
        System.out.println("GAME INVITATION");
        System.out.println("===========================================");
        System.out.println(inviter + " has challenged you to a Heads or Tails game!");
        System.out.println("Type 'yes' to accept or 'no' to decline");
        System.out.println("===========================================\n");
    }

    /**
     * Processes user's response to game invitation
     */
    public Command handleGameResponse(String input) {
        String inviter = pendingInviter.get();

        if (inviter == null) {
            System.out.println("No pending game invite to respond to.");
            return null;
        }

        String response = input.trim().toLowerCase();
        boolean accept = false;

        if (response.equals("yes") || response.equals("y")) {
            accept = true;
            System.out.println("You accepted the game invitation!");
        } else if (response.equals("no") || response.equals("n")) {
            accept = false;
            System.out.println("You declined the game invitation.");
        } else {
            System.out.println("Invalid response. Type 'yes' to accept or 'no' to decline.");
            return null;
        }

        pendingInviter.set(null);

        if (accept) {
            isInGame.set(true);
            opponent.set(inviter);
        }

        return new GameResponseCommand(inviter, accept);
    }

    /**
     * Starts a new game
     */
    public void startGame(String opponentName, int round) {
        synchronized (lock) {
            this.isInGame.set(true);
            this.opponent.set(opponentName);
            this.currentRound.set(round);
            this.waitingForChoice.set(true);
            this.choiceSubmitted.set(false);
        }

        System.out.println("\n===========================================");
        System.out.println("GAME STARTED - HEADS OR TAILS");
        System.out.println("===========================================");
        System.out.println("Playing against: " + opponentName);
        System.out.println("First to 3 points wins!");
        System.out.println("===========================================\n");
        promptForChoice();
    }

    /**
     * Starts a new round
     */
    public void startNewRound(int round) {
        synchronized (lock) {
            this.currentRound.set(round);
            this.waitingForChoice.set(true);
            this.choiceSubmitted.set(false);
        }
        promptForChoice();
    }

    /**
     * Prompts user to make their choice
     */
    private void promptForChoice() {
        System.out.println("\nRound " + currentRound.get());
        System.out.println("Type 'heads' or 'tails' to make your choice:");
    }

    /**
     * Processes user's choice input
     */
    public Command handleChoiceInput(String input) {
        if (!isInGame.get() || !waitingForChoice.get() || choiceSubmitted.get()) {
            return null;
        }

        String choice = input.trim().toLowerCase();
        if (!choice.equals("heads") && !choice.equals("tails")) {
            System.out.println("Invalid choice. Please type 'heads' or 'tails'.");
            return null;
        }

        choiceSubmitted.set(true);
        waitingForChoice.set(false);

        return new GameChoiceCommand(choice);
    }

    /**
     * Handles game end
     */
    public void handleGameEnd() {
        System.out.println("\n===========================================");
        System.out.println("GAME OVER");
        System.out.println("===========================================\n");
        reset();
    }

    /**
     * Handles invitation acceptance/rejection from opponent
     */
    public void handleInviteResponse(String username, boolean accepted) {
        if (accepted) {
            System.out.println(username + " accepted your game invitation!");
            opponent.set(username);
            isInGame.set(true);
        } else {
            System.out.println(username + " declined your game invitation.");
            reset();
        }
    }

    public void clearPendingInviter() {
        pendingInviter.set(null);
    }

    public String getPendingInviter() {
        return pendingInviter.get();
    }

    public boolean isInGame() {
        return isInGame.get();
    }

    public boolean isWaitingForChoice() {
        return waitingForChoice.get();
    }

    public boolean isChoiceSubmitted() {
        return choiceSubmitted.get();
    }

    public String getOpponent() {
        return opponent.get();
    }

    public int getCurrentRound() {
        return currentRound.get();
    }
}