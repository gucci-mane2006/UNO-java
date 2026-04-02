package uno.java;

enum Color {
    RED, 
    YELLOW, 
    GREEN, 
    BLUE, 
    WILD;
};

enum Type {
    NORMAL,
    WILD, 
    SKIP, 
    REVERSE,
    DRAW_TWO,
    DRAW_FOUR;

    boolean isWildType() {
        return this == WILD || this == DRAW_FOUR;
    }

    boolean isActionType() {
        return this == SKIP || this == REVERSE || this == DRAW_TWO;
    }

    boolean isNormalType() {
        return this == NORMAL;
    }
};

public final class Card {
    private final Color color;
    private final Type type;
    private final Integer number; // null for non-numbered cards
	
	// Private constructor - use the below factories to instantiate cards instead
    private Card (Color color, Type type, Integer number) {
        this.color = color;
        this.type = type;
        this.number = number;
        validateCard();
    }

    // Numbered card factory (0-9)
    // Usage in main example: Card c = Card.numberCard(Color.RED, 5); 
    public static Card numberCard(Color color, int number) {
        if (color == null) throw new IllegalArgumentException("Color cannot be null");

        return new Card(color, Type.NORMAL, number);
    }

    // Action card factory
    // Usage in main example: Card c = Card.actionCard(Color.RED, Type.DRAW_TWO);
    public static Card actionCard(Color color, Type actionType) {
        if (color == null) throw new IllegalArgumentException("color cannot be null");
        if (actionType == null) throw new IllegalArgumentException("actionType cannot be null");
        if (!actionType.isActionType()) throw new IllegalArgumentException("actionType must be SKIP, REVERSE, or DRAW_TWO");
        
        return new Card(color, actionType, null);
    }

    // Wild card factory
    // Usage in main example: Card c = Card.wildCard();
    public static Card wildCard() {
        return new Card(Color.WILD, Type.WILD, null);
    }

    // Draw four card factory
    // Usage in main example: Card c = Card.drawFourCard();
    public static Card drawFourCard() {
        return new Card(Color.WILD, Type.DRAW_FOUR, null);
    }

    // Validating cards
    private void validateCard() {
        // Number rules
        if (type.isNormalType()) {
            if (number == null) throw new IllegalArgumentException("NORMAL cards must have a number");
            if (number < 0 || number > 9) throw new IllegalArgumentException("number must be between 0 and 9");
        } 
        else {
            if (number != null) throw new IllegalArgumentException("Only NORMAL cards have a number");
        }

        // Wild-type color rules
        if (type.isWildType()) {
            if (color != Color.WILD) throw new IllegalArgumentException("Type.WILD cards must also have Color.WILD");
        }
        else {
            if (color == Color.WILD) throw new IllegalArgumentException("Only Type.WILD or Type.DRAW_FOUR may have Color.WILD");
        }
    }

    // Get methods
    public Color getColor()     { return color; }
    public Type getType()       { return type; }
    public Integer getNumber()  { return number; }

    @Override
    public String toString() {
        if (type.isNormalType()) return "[" + color + " " + number + "]";

        if (type == Type.WILD) {
            return "[WILD CARD]";
        }

        if (type == Type.DRAW_FOUR) {
            return "[DRAW FOUR]";
        }

        return "[" + color + " " + type + "]";
    }
}
