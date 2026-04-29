package uno.java.player;

import java.util.*;
import uno.java.core.*;

public class PlayerStrategyRandom implements PlayerStrategy {
    private final Random random;

    public PlayerStrategyRandom() {
        this.random = new Random();
    }

    public PlayerStrategyRandom(long seed) {
        this.random = new Random(seed);
    }

    // Implementation
    @Override
    public Card chooseCard(List<Card> hand, GameState state) {
        List<Card> playable = hand.stream().filter(state::isCardPlayable).toList();

        if (playable.isEmpty()) return null;

        return playable.get(random.nextInt(playable.size()));
    }

    @Override
    public Color chooseColor(List<Card> hand) {
        Color[] suitedColors = { Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE };
        return suitedColors[random.nextInt(suitedColors.length)];
    }
}
