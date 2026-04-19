package uno.java;

import java.util.*;

public class GameController {
    private final GameState state;
    private final Deck deck;
    private final ScoreManager scoreManager;
    private boolean unoCalledThisTurn;

    public GameController(List<Player> players, int targetScore) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("Game requires at least 2 players");
        
        this.state              = new GameState(players, targetScore);
        this.deck               = new Deck();
        this.scoreManager       = new ScoreManager();
        this.unoCalledThisTurn  = false;
        dealInitialHands();
    }

    /*
        PUBLIC API
    */

    public void startGame() {

    }
    
    public void callUno() {

    }

    public GameState getState() { return state; }

    /*
        PRIVATE LOGIC
    */

    // Round loop
    private void playRound() {}
    
    private void playTurn() {}

    // Card effects
    private void applyCardEffect(Card card, Player player) {}

    // Player advancement
    private void advancePlayer(int steps) {
        int next = state.getNextPlayerIndex(steps);
        state.setCurrentPlayerIndex(next);
    }

    // Draw handling
    private void handleDraw(Player player, int count) {
        for (int i=0; i<count; i++) {
            Card draw = deck.draw();
            player.addCard(draw);
        }
    }

    // UNO check
    private void checkUno(Player player) {
        if (player.getHandSize() == 1 && !unoCalledThisTurn) {
            // TODO: event for uno penalty
            handleDraw(player, 2);
        }
    }

    // Color resolution

    // Round boundaries

    // Helpers
    private void dealInitialHands() {
        // stub for now
    }
}
