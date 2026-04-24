package uno.java;

public class CardDTO {
    Color color;
    Type type;
    Integer number;
    
    // Gson no-arg constructor
    public CardDTO() {}
    
    // Factory - Card to DTO
    public static CardDTO from(Card card) {
        if (card == null) throw new IllegalArgumentException("Cannot create a CardDTO from a null card");
        
        CardDTO dto = new CardDTO();
        dto.color  = card.getColor();
        dto.type   = card.getType();
        dto.number = card.getNumber();
        return dto;
    }
    
    // Reconstruct live Card from DTO
    public Card toCard() {
        if (type == null) throw new IllegalStateException("CardDTO has null type — data may be corrupt");
 
        try {
            return switch (type) {
                case NORMAL    -> Card.numberCard(color, number);
                case WILD      -> Card.wildCard();
                case DRAW_FOUR -> Card.drawFourCard();
                default        -> Card.actionCard(color, type);
            };
        }
        catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "CardDTO could not be reconstructed — data may be corrupt. "
                + "color=" + color + ", type=" + type + ", number=" + number, e);
        }
    }
    
    // Override
    @Override
    public String toString() {
        return "CardDTO{color=" + color + ", type=" + type + ", number=" + number + "}";
    }
}
