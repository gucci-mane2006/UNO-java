package uno.java;

import java.util.*;

public class PlayerHuman extends Player {
    private final InputHandler inputHandler;

    public PlayerHuman(String id, String name, InputHandler inputHandler) {
        super(id, name);
        if (inputHandler == null) throw new IllegalArgumentException("InputHandler cannot be null");
        this.inputHandler = inputHandler;
    }
    
    public InputHandler getInputHandler() { return inputHandler; }
    
    @Override
    public Card takeTurn(GameState state) {
        List<Card> playable = getPlayableCards(state);

        if (playable.isEmpty()) {
            inputHandler.showMessage("No playable cards - you must draw");
            return null;
        }

        return inputHandler.selectCard(hand, state);
    }
}
