package uno.java.core;

import java.util.*;

public class Deck {
    private Deque<Card> drawPile    = new ArrayDeque<>();
    private Deque<Card> discardPile = new ArrayDeque<>();

    private static final int DECK_SIZE = 108;

    public Deck() {
        initialiseDeck();
        assert drawPile.size() == DECK_SIZE : "Deck init with " + drawPile.size() + "cards, expected " + DECK_SIZE;
        shuffle();
    }

    /*
        PRIVATE SETUP
    */

    private void initialiseDeck() {
        Color[] colors = { Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE };

        for (Color c : colors) {
            // One 0 per color
            drawPile.add(Card.numberCard(c, 0));

            // Two of each 1-9 per color
            for (int i=1; i<=9; i++) {
                drawPile.add(Card.numberCard(c, i));
                drawPile.add(Card.numberCard(c, i));
            }

            // Two of each action card per color
            Type[] actions = { Type.SKIP, Type.REVERSE, Type.DRAW_TWO };
            for (Type t : actions) {
                drawPile.add(Card.actionCard(c, t));
                drawPile.add(Card.actionCard(c, t));
            }
        }

        // Four of each wild
        for (int i=0; i<4; i++) {
            drawPile.add(Card.wildCard());
            drawPile.add(Card.drawFourCard());
        }
    }

    private void shuffle() {
        List<Card> newDrawPile = new ArrayList<>(drawPile);
        Collections.shuffle(newDrawPile);
        drawPile.clear();
        drawPile.addAll(newDrawPile);
    }

    private void reshuffleDiscardIntoDraw() {
        if (discardPile.size() <= 1) {
            return;
        }

        Card topCard = discardPile.pop();

        List<Card> cards = new ArrayList<>(discardPile);
        Collections.shuffle(cards);
        discardPile.clear();
        drawPile.addAll(cards);

        discardPile.push(topCard);
    }

    /*
        PUBLIC API
    */

    public Card draw() {
        if (drawPile.isEmpty()) reshuffleDiscardIntoDraw();
        if (drawPile.isEmpty()) throw new IllegalStateException("Draw pile exhausted - not enough cards to draw");
        return drawPile.pop();
    }

    public void discard(Card card) {
        if (card == null) throw new IllegalArgumentException("Discard was called for a null card");
        discardPile.push(card);
    }
    
    public void insertIntoDrawPile(Card card) {
        if (card == null) throw new IllegalArgumentException("card cannot be null");
        List<Card> list = new ArrayList<>(drawPile);
        list.add(new Random().nextInt(list.size() + 1), card);
        drawPile.clear();
        drawPile.addAll(list);
    }

    public Card peekTopDiscard() {
        if (discardPile.isEmpty()) throw new IllegalStateException("Discard pile is empty");
        return discardPile.peek();
    }

    public int drawPileSize() {
        return drawPile.size();
    }

    public boolean isEmpty() {
        return drawPile.isEmpty();
    }
}
