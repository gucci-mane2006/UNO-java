package uno.java;

import java.util.*;
import java.nio.file.Path;

public class GameController {
    private final GameState state;
    private final Deck deck;
    private final ScoreManager scoreManager;
    private final GameSaveManager saveManager;
    private final ProfileRepository profileRepository;
    private boolean unoCalledThisTurn;
    
    // Constructor
    public GameController(List<Player> players, int targetScore, Path saveDirectory, Path profilesFile) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("Game requires at least 2 players");
        
        this.state              = new GameState(players, targetScore);
        this.deck               = new Deck();
        this.scoreManager       = new ScoreManager();
        this.saveManager        = new GameSaveManager(saveDirectory);
        this.profileRepository  = new ProfileRepository(profilesFile);
        this.unoCalledThisTurn  = false;
        
        dealInitialHands();
    }

    /*
        PUBLIC API
    */

    public void startGame() {
        state.setPhase(GamePhase.IN_PROGRESS);

    }
    
    public void callUno() {
        this.unoCalledThisTurn = true; //uno called true

    }

    public GameState getState() { return state; }

    /*
        PRIVATE LOGIC
    */

    // Round loop
    private void playRound() {
        state.setPhase(GamePhase.IN_PROGRESS);
        while (!state.isRoundOver()) playTurn();
        state.setPhase(GamePhase.ROUND_OVER);
    }
    
    private void playTurn() {
        Player current = state.getCurrentPlayer();
        Card played = current.takeTurn(state);
        if (played == null) {
            Card drawn = deck.draw();
            current.addCard(drawn);
            if (state.isCardPlayable(drawn)) {
                current.removeCard(drawn);
                playCard(drawn, current);
            } else {
                advancePlayer(1);
                unoCalledThisTurn = false;
            }
        } else {
            if (!current.removeCard(played)) throw new IllegalStateException("Player attempted to play a card they don't have");
            playCard(played, current);
        }
        checkUno(current);
    }
    private void playCard(Card card, Player player) {
        deck.discard(card);
        state.setTopCard(card);
        if (card.getType().isWildType()) {
            Color chosenColor = (player instanceof PlayerAI)
                ? ((PlayerAI) player).getStrategy().chooseColor(player.getHand())
                : Color.RED;
            state.setCurrentColor(chosenColor);
        } else {
            state.setCurrentColor(card.getColor());
        }
        applyCardEffect(card, player);
        if (!state.isRoundOver()) advancePlayer(1);
    }

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
        unoCalledThisTurn = false;
    }

    // Color resolution

    // Round boundaries

    // Helpers
    private void dealInitialHands() {
        // stub for now
        for (int i = 0; i < 7; i++) 
            for (Player p : state.getPlayers()) p.addCard(deck.draw());
    }
    
}
