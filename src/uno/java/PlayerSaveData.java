package uno.java;

import java.util.List;

public class PlayerSaveData {
    String id;
    String name;
    int score;
    boolean isAI;
    List<CardDTO> hand;
    
    // Gson no-arg constructor
    public PlayerSaveData() {}
    
    // Full constructor
    public PlayerSaveData(
            String id,
            String name,
            int score,
            boolean isAI,
            List<CardDTO> hand
    ) {
        if (id == null || id.isBlank())     throw new IllegalArgumentException("Id cannot be null or blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name cannot be null or blank");
        if (score < 0)                      throw new IllegalArgumentException("Score cannot be negative");
        if (hand == null)                   throw new IllegalArgumentException("Hand cannot be null");
        
        this.id    = id;
        this.name  = name;
        this.score = score;
        this.isAI  = isAI;
        this.hand  = hand;
    }
    
    // Factory
    public static PlayerSaveData from(Player player) {
        if (player == null) throw new IllegalArgumentException("Cannot create save data from a null player");
 
        List<CardDTO> handDTOs = player.getHand()
                .stream()
                .map(CardDTO::from)
                .toList();
 
        return new PlayerSaveData(
                player.getId(),
                player.getName(),
                player.getScore(),
                player instanceof PlayerAI,
                handDTOs
        );
    }

    // Getters
    public String getId()           { return id; }
    public String getName()         { return name; }
    public int getScore()           { return score; }
    public boolean isAI()           { return isAI; }
    public List<CardDTO> getHand()  { return hand; }

    public List<Card> rebuildHand() {
        return hand.stream()
                .map(CardDTO::toCard)
                .toList();
    }
    
    // Override
    @Override
    public String toString() {
        return "PlayerSaveData{id='" + id + "', name='" + name + "', score=" + score
                + ", isAI=" + isAI + ", handSize=" + (hand != null ? hand.size() : 0) + "}";
    }
}
