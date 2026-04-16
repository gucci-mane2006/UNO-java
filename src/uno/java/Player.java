package uno.java;

import java.util.*;

public abstract class Player {
    protected String id;
    protected String name;
    protected List<Card> hand;
    protected int score;

    protected Player(String id, String name) {
        this.id = id;
        this.name = name;
        hand = new ArrayList<Card>();
    }
}
