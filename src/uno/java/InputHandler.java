package uno.java;

import java.util.*;

public interface InputHandler {
    Card selectCard(List<Card> hand, GameState state);
    Color selectColor();
    void showMessage(String message);
}
