package uno.java;

public class PlayerAI extends Player {
    private PlayerStrategy strategy;

    public PlayerAI(String id, String name, PlayerStrategy strategy) {
        super(id, name);
        if (strategy == null) throw new IllegalArgumentException("PlayerStrategy cannot be null");
        this.strategy = null;
    }

    @Override
    public Card takeTurn(GameState state) {
        if (!hasPlayableCard(state)) {
            return null;
        }
        return strategy.chooseCard(hand, state);
    }

    public void setStrategy(PlayerStrategy strategy) {
        if (strategy == null) throw new IllegalArgumentException("PlayerStrategy cannot be null.");
        this.strategy = strategy;
    }

    public PlayerStrategy getStrategy() { return strategy; }
}
