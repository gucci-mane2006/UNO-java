package uno.java.dto;

import java.util.List;

// Serialisable representation of a snapshot of a game in progress
// - written after every turn
public class GameSaveDTO {
    public int                  roundNumber;
    public int                  currentPlayerIndex;
    public boolean              clockwise;
    public String               currentColor;
    public CardDTO              topCard;
    public List<PlayerSaveDTO>  players;
    public List<CardDTO>        drawPile;
    public List<CardDTO>        discardPile;
    
    public GameSaveDTO() {}
}
