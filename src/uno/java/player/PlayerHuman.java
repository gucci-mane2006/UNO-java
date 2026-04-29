package uno.java.player;

import java.util.*;

import uno.java.controller.GameState;
import uno.java.core.*;
import uno.java.input.*;

public class PlayerHuman extends Player {
    private final InputHandler inputHandler;
    private boolean lastUnoCall = false;

    public PlayerHuman(String id, String name, InputHandler inputHandler) {
        super(id, name);
        if (inputHandler == null) throw new IllegalArgumentException("InputHandler cannot be null");
        this.inputHandler = inputHandler;
    }
    
    public InputHandler getInputHandler() { return inputHandler; }
    
    @Override
    public boolean didCallUno() { return lastUnoCall; }

    @Override
    public Card takeTurn(GameState state) {
        List<Card> playable = getPlayableCards(state);

        if (playable.isEmpty()) {
            inputHandler.showMessage("No playable cards - you must draw");
            return null;
        }

        CardSelection selection = inputHandler.selectCard(getHand(), state);
        lastUnoCall = selection.unoCalled();
        return selection.card();
    }
}
