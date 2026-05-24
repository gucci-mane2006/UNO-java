package uno.java.input;

import java.util.*;

import uno.java.controller.GameState;
import uno.java.core.*;

public interface InputHandler {
    CardSelection selectCard(List<Card> hand, GameState state);
    Color selectColor();
    void showMessage(String message);
}
