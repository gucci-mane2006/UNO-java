package uno.java.dto;

import java.util.List;

// Serialisable representation of a snapshot of one player's ingame state
public class PlayerSaveDTO {
    public String           id;
    public String           name;
    public int              score;
    public String           playerType; // "HUMAN" or "AI"
    public List<CardDTO>    hand;
    
    public PlayerSaveDTO() {}
}
