package uno.java.dto;

// Serialisable representation of a Card
public class CardDTO {
    public String   color;
    public String   type;
    public Integer  number;
    
    public CardDTO() {}
    
    public CardDTO(String color, String type, Integer number) {
        this.color  = color;
        this.type   = type;
        this.number = number;
    }
}
