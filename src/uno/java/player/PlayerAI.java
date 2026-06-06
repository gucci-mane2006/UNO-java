package uno.java.player;

import java.util.Random;

import uno.java.controller.GameState;
import uno.java.core.*;

public class PlayerAI extends Player {
    private static final double UNO_CALL_CHANCE = 0.80; // 80% chance the AI remembers to call UNO

    private PlayerStrategy strategy;
    private final Random   random;

    private boolean calledUnoThisTurn = false;

    public PlayerAI(String id, String name, PlayerStrategy strategy) {
        this(id, name, strategy, new Random());
    }

    // Seeded constructor for deterministic testing
    public PlayerAI(String id, String name, PlayerStrategy strategy, Random random) {
        super(id, name);
        if (strategy == null) throw new IllegalArgumentException("PlayerStrategy cannot be null");
        if (random   == null) throw new IllegalArgumentException("Random cannot be null");
        this.strategy = strategy;
        this.random   = random;
    }

    @Override
    public Card takeTurn(GameState state) {
        calledUnoThisTurn = false; // reset each turn

        if (!hasPlayableCard(state)) {
            return null;
        }

        Card chosen = strategy.chooseCard(getHand(), state);

        // If playing this card would leave exactly 1 card in hand,
        // the AI should call UNO — but has a 20% chance of forgetting.
        if (chosen != null && getHandSize() == 2) {
            calledUnoThisTurn = random.nextDouble() < UNO_CALL_CHANCE;
        }

        return chosen;
    }

    @Override
    public boolean didCallUno() {
        return calledUnoThisTurn;
    }

    public void setStrategy(PlayerStrategy strategy) {
        if (strategy == null) throw new IllegalArgumentException("PlayerStrategy cannot be null.");
        this.strategy = strategy;
    }

    public PlayerStrategy getStrategy() { return strategy; }
}