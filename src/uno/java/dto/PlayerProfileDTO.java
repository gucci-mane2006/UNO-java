package uno.java.dto;

// Persistent profile for a human player stored in profiles.json
public class PlayerProfileDTO {
    public String id;
    public String name;
    public int    score;
    
    public PlayerProfileDTO() {}
    
    public PlayerProfileDTO(String id, String name, int score) {
        this.id     = id;
        this.name   = name;
        this.score  = score;
    }
}
