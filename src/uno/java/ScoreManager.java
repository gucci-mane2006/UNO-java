package uno.java;

import java.util.*;

public class ScoreManager {
    // stub for now
    public ScoreManager() {}
    
    public int calculatePoints(List<Player> players, Player winner) {
        int total = 0;
        for (Player p : players) {
            if (p == winner) continue;
            for (Card c : p.getHand()) {
                total += pointsForCard(c);
            }
        }
        return total;
    }

    public int pointsForCard(Card c) {
        switch (c.getType()) {
            case NORMAL:
                return c.getNumber();
            case SKIP:
            case REVERSE:
            case DRAW_TWO:
                return 20;
            case WILD:
            case DRAW_FOUR:
                return 50;
            default:
                return 0;
        }
    }
}
