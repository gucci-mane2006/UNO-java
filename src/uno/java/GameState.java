package uno.java;

import java.util.*;

public class GameState {
    private final List<Player> players;
    private int currentPlayerIndex;
    private boolean clockwise;
    private Card topCard;
    private Color currentColor;
    private int roundNumber;
    private final int targetScore;
    private GamePhase phase;

    public GameState(List<Player> players, int targetScore) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("Game requires at least 2 players");
        if (targetScore <= 0) throw new IllegalArgumentException("Target score must be greater than 0");

        this.players = new ArrayList<>(players);
        this.targetScore = targetScore;
        this.currentPlayerIndex = 0;
        this.clockwise = true;
        this.roundNumber = 1;
        this.phase = GamePhase.WAITING;
        this.topCard = null;
        this.currentColor = null;
    }

    /*
        PUBLIC API 
    */
   
    public Player getPlayer(int index) {
        if (index < 0 || index >= players.size()) throw new IndexOutOfBoundsException("Player index out of range: " + index);
        return players.get(index);
    }
    public Player getCurrentPlayer()    { return players.get(currentPlayerIndex); }
    public List<Player> getPlayers()    { return Collections.unmodifiableList(players); }
    public int getCurrentPlayerIndex()  { return currentPlayerIndex; }
    public int getTargetScore()         { return targetScore; }
    public Card getTopCard()            { return topCard; }
    public Color getCurrentColor()      { return currentColor; }
    public boolean isClockwise()        { return clockwise; }
    public int getRoundNumber()         { return roundNumber; }
    public GamePhase getPhase()         { return phase; }
    public int getPlayerCount()         { return players.size(); }

    public int getNextPlayerIndex(int steps) {
        int count = players.size();
        if (clockwise)  return (currentPlayerIndex + steps) % count;
        else            return (currentPlayerIndex - steps + count) % count;
    }

    public Player getNextPlayer() { return players.get(getNextPlayerIndex(1)); }

    public boolean isCardPlayable(Card card) {
        if (card == null) return false;
        if (card.getType() == Type.WILD || card.getType() == Type.DRAW_FOUR) return true;
        return card.getColor() == currentColor
        || card.getType() == topCard.getType()
        || (card.getType() == Type.NORMAL && card.getNumber() == topCard.getNumber());
    }

    public boolean isRoundOver()                { return players.stream().anyMatch(p -> p.getHandSize() == 0); }
    public boolean isGameOver()                 { return players.stream().anyMatch(p -> p.getScore() >= targetScore); }
    public Optional<Player> getLeader()         { return players.stream().max(Comparator.comparingInt(Player::getScore)); }
    public Optional<Player> getRoundWinner()    { return players.stream().filter(p -> p.getHandSize() == 0).findFirst(); }

    /*
        PRIVATE SETTERS
    */

    void setCurrentPlayerIndex(int index) {
        if (index < 0 || index >= players.size()) throw new IndexOutOfBoundsException("Player index out of range: " + index);
        this.currentPlayerIndex = index;
    }

    void setClockwise(boolean clockwise) { this.clockwise = clockwise; }

    void setTopCard(Card card) {
        if (card == null) throw new IllegalArgumentException("Top card cannot be null");
        this.topCard = card;
    }

    void setCurrentColor(Color color) {
        if (color == null || color == Color.WILD) throw new IllegalArgumentException("Current color cannot be null or wild");
        this.currentColor = color;
    }

    void setPhase(GamePhase phase) {
        if (phase == null) throw new IllegalArgumentException("Phase cannot be null");
        this.phase = phase;
    }

    void incrementRound() { this.roundNumber++; }
}
