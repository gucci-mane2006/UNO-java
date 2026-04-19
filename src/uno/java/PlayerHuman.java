package uno.java;

import java.util.*;

public class PlayerHuman extends Player {
    private final InputHandler inputHandler;

    public PlayerHuman(String id, String name, InputHandler inputHandler) {
        super(id, name);
        if (inputHandler == null) throw new IllegalArgumentException("InputHandler cannot be null");
        this.inputHandler = inputHandler;
    }

    @Override
    public Card takeTurn(GameState state) {
        inputHandler.showMessage("\nYour hand:" + hand);
        inputHandler.showMessage("Top card: " + state.getTopCard() + " | Color: " + state.getCurrentColor());

        List<Card> playable = getPlayableCards(state);

        if (playable.isEmpty()) {
            inputHandler.showMessage("No playable cards - you must draw");
            return null;
        }

        Card chosen = inputHandler.selectCard(hand, state);

        while (!state.isCardPlayable(chosen)) {
            inputHandler.showMessage("That card is not playable - choose a different card");
            chosen = inputHandler.selectCard(hand, state);
        }

        return chosen;
    }
}
