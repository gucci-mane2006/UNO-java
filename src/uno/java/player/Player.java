package uno.java.player;

import java.util.*;

import uno.java.controller.GameState;
import uno.java.core.*;

public abstract class Player {
    protected String id;
    protected String name;
    protected List<Card> hand;
    protected int score;

    protected Player(String id, String name) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Player id cannot be null or blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Player name cannot be null or blank");

        this.id = id;
        this.name = name;
        this.hand = new ArrayList<>();
        this.score = 0;
    }

    public abstract Card takeTurn(GameState state);

    public boolean didCallUno() { return false; }

    // Hand management
    public void addCard(Card card) {
        if (card == null) throw new IllegalArgumentException("Cannot add a null card to hand");
        hand.add(card);
    }

    public boolean removeCard(Card card)                { return hand.remove(card); }
    public List<Card> getHand()                         { return Collections.unmodifiableList(hand); }
    public int getHandSize()                            { return hand.size(); }
    public boolean hasPlayableCard(GameState state)     { return hand.stream().anyMatch(state::isCardPlayable); }
    public List<Card> getPlayableCards(GameState state) { return hand.stream().filter(state::isCardPlayable).toList(); }

    // Score management
    public int getScore() { return score; }

    public void addScore(int score) {
        if (score < 0) throw new IllegalArgumentException("Cannot add negative score");
        this.score += score;
    }

    // Id
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Player name cannot be null or blank");
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " (wins: " + score + " , cards: " + hand.size() + ")";
    }
}
