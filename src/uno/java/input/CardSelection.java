package uno.java.input;

import uno.java.core.Card;

public record CardSelection(Card card, boolean unoCalled) {
    public boolean isDrawing() { return card == null; }
}
