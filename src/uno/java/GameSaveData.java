package uno.java;

import java.util.List;

public class GameSaveData {
    private String saveId;
    private long savedAt;    // epoch ms - used to sort/display saves
    
    // GameState fields
    private int currentPlayerIndex;
    private int targetScore;
    private boolean clockwise;
    private int roundNumber;
    private String phase;
    private CardDTO topCard;
    private String currentColor;
    private List<PlayerSaveData> players;
    
    // Gson no-arg constructor
    public GameSaveData() {}
    
    // Full constructor
    public GameSaveData(
            String saveId,
            long savedAt,
            int currentPlayerIndex,
            int targetScore,
            boolean clockwise,
            int roundNumber,
            String phase,
            CardDTO topCard,
            String currentColor,
            List<PlayerSaveData> players
    ) {
        this.saveId               = saveId;
        this.savedAt              = savedAt;
        this.currentPlayerIndex   = currentPlayerIndex;
        this.targetScore          = targetScore;
        this.clockwise            = clockwise;
        this.roundNumber          = roundNumber;
        this.phase                = phase;
        this.topCard              = topCard;
        this.currentColor         = currentColor;
        this.players              = players;
    }
    
    // Getters
    public String           getSaveId()             { return saveId; }
    public long             getSavedAt()            { return savedAt; }
    public int              getCurrentPlayerIndex() { return currentPlayerIndex; }
    public int              getTargetScore()        { return targetScore; }
    public boolean          isClockwise()           { return clockwise; }
    public int              getRoundNumber()        { return roundNumber; }
    public CardDTO          getTopCard()            { return topCard; }
    public List<PlayerSaveData> getPlayers()        { return players; }
    
    public GamePhase getPhase() {
        return GamePhase.valueOf(phase);
    }

    public Color getCurrentColor() {
        Color color = Color.valueOf(currentColor);
        if (color == Color.WILD) throw new IllegalStateException("Saved currentColor cannot be WILD");
        return color;
    }

    // Override
    @Override
    public String toString() {
        return "GameSaveData{saveId='" + saveId + "', savedAt=" + savedAt
                + ", phase=" + phase + ", round=" + roundNumber
                + ", players=" + (players != null ? players.size() : 0) + "}";
    }
}
