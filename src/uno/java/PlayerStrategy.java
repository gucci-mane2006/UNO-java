package uno.java;

import java.util.*;

public interface PlayerStrategy {
    Card chooseCard(List<Card> hand, GameState state);
    Color chooseColor(List<Card> hand);
}