package uno.java;

import java.util.*;

public class ScoreManager {
    // stub for now
    public ScoreManager() {}
    
    public int calculateRoundScore(List<Player> players, Player winner) {
        return players.stream()
                .filter(p -> !p.equals(winner))
                .flatMap(p -> p.getHand().stream())
                .mapToInt(this::cardValue)
                .sum();
    }

    private int cardValue(Card card) {
        return switch (card.getType()) {
            case NORMAL    -> card.getNumber();
            case SKIP,
                 REVERSE,
                 DRAW_TWO  -> 20;
            case WILD,
                 DRAW_FOUR -> 50;
        };
    }
}
