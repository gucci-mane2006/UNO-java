package uno.java.player;

import java.util.*;

import uno.java.controller.GameState;
import uno.java.core.*;

public interface PlayerStrategy {
    Card chooseCard(List<Card> hand, GameState state);
    Color chooseColor(List<Card> hand);
}