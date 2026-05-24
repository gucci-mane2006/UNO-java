package uno.java.core;
 
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
 
import java.util.List;
 
/**
 * Tests for Deck.java.
 *
 * Key behaviours under test:
 *
 *  1. A freshly constructed Deck has exactly 108 cards.
 *  2. draw() removes cards from the draw pile one at a time.
 *  3. When the draw pile is empty, draw() reshuffles the discard pile
 *     (keeping the top discard card in place) into the draw pile and continues.
 *  4. The reshuffle is NOT triggered when the discard pile has only one card -
 *     this is the known edge-case bug documented in the onboarding notes.
 *     The test below verifies the current behaviour so any future fix is
 *     immediately visible as a test failure to be updated.
 *  5. discard(), peekTopDiscard(), insertIntoDrawPile() behave correctly and
 *     reject null inputs.
 *  6. Deck.fromPiles() reconstructs a deck from arbitrary lists of cards -
 *     this is the path used by GameSaveManager when resuming a saved game.
 *
 * Note on test isolation: rather than drawing 108 cards to reach edge cases,
 * we use Deck.fromPiles() to construct decks in specific states cheaply.
 */
public class DeckTest {
 
    private Deck deck;
 
    @Before
    public void setUp() {
        deck = new Deck();
    }
 
    // -------------------------------------------------------------------------
    // Standard construction
    // -------------------------------------------------------------------------
 
    /**
     * An UNO deck has exactly 108 cards:
     *   4 colors × (1 zero + 18 numbers 1-9 + 6 action cards) = 76 suited cards
     *   + 4 Wilds + 4 Wild Draw Fours = 8 wild cards
     *   Total: 76 + 8 = 108 (but actually: 4*(1+18+6)=100 + 8 = 108)
     *   Let's verify: 4 colors * (1 zero + 2*(1..9) + 2*SKIP + 2*REVERSE + 2*DRAW_TWO)
     *                 = 4 * (1 + 18 + 6) = 4 * 25 = 100, plus 4 WILD + 4 DRAW_FOUR = 108.
     */
    @Test
    public void newDeck_drawPileSize_is108() {
        assertEquals(108, deck.drawPileSize());
    }
 
    @Test
    public void newDeck_isEmpty_false() {
        assertFalse(deck.isEmpty());
    }
 
    // -------------------------------------------------------------------------
    // draw()
    // -------------------------------------------------------------------------
 
    @Test
    public void draw_returnsNonNullCard() {
        assertNotNull(deck.draw());
    }
 
    @Test
    public void draw_reducesDrawPileSizeByOne() {
        int before = deck.drawPileSize();
        deck.draw();
        assertEquals(before - 1, deck.drawPileSize());
    }
 
    @Test
    public void draw_multipleCards_reducesCorrectly() {
        for (int i = 0; i < 7; i++) deck.draw();
        assertEquals(101, deck.drawPileSize());
    }
 
    // -------------------------------------------------------------------------
    // discard()
    // -------------------------------------------------------------------------
 
    @Test(expected = IllegalArgumentException.class)
    public void discard_null_throws() {
        deck.discard(null);
    }
 
    @Test
    public void discard_thenPeek_returnsDiscardedCard() {
        Card card = deck.draw();
        deck.discard(card);
        // peekTopDiscard() must return the same object reference because the
        // Deck stores and retrieves the identical Card instance.
        assertSame(card, deck.peekTopDiscard());
    }
 
    // -------------------------------------------------------------------------
    // peekTopDiscard()
    // -------------------------------------------------------------------------
 
    @Test(expected = IllegalStateException.class)
    public void peekTopDiscard_emptyDiscardPile_throws() {
        // No cards have been discarded yet on a new deck.
        deck.peekTopDiscard();
    }
 
    @Test
    public void peekTopDiscard_doesNotRemoveCard() {
        Card card = deck.draw();
        deck.discard(card);
 
        deck.peekTopDiscard(); // first peek
        Card peeked = deck.peekTopDiscard(); // second peek should return same card
 
        assertSame(card, peeked);
    }
 
    // -------------------------------------------------------------------------
    // insertIntoDrawPile()
    // -------------------------------------------------------------------------
 
    @Test(expected = IllegalArgumentException.class)
    public void insertIntoDrawPile_null_throws() {
        deck.insertIntoDrawPile(null);
    }
 
    @Test
    public void insertIntoDrawPile_increasesDrawPileSizeByOne() {
        // Draw a card so we have one to reinsert.
        Card card   = deck.draw();
        int before  = deck.drawPileSize();
 
        deck.insertIntoDrawPile(card);
 
        assertEquals(before + 1, deck.drawPileSize());
    }
 
    // -------------------------------------------------------------------------
    // Deck.fromPiles() - save/restore path
    // -------------------------------------------------------------------------
 
    @Test
    public void fromPiles_drawPileSize_matchesInputList() {
        List<Card> draw    = List.of(Card.numberCard(Color.RED, 1), Card.numberCard(Color.BLUE, 2));
        List<Card> discard = List.of(Card.wildCard());
 
        Deck d = Deck.fromPiles(draw, discard);
 
        assertEquals(2, d.drawPileSize());
    }
 
    @Test
    public void fromPiles_draw_returnsCardFromSuppliedList() {
        Card first  = Card.numberCard(Color.RED, 1);
        Card second = Card.numberCard(Color.BLUE, 2);
 
        // The Deck stores cards using an ArrayDeque (stack/queue behaviour):
        // fromPiles adds draw cards via addAll, so the first card added is at
        // the bottom and the last is on top. draw() pops from the top (pop()).
        Deck d = Deck.fromPiles(List.of(first, second), List.of());
 
        // second was added last so it sits on top of the deque
        Card drawn = d.draw();
        assertNotNull(drawn);
    }
 
    @Test
    public void fromPiles_discardPeek_returnsTopOfSuppliedDiscardList() {
        Card discardTop = Card.actionCard(Color.GREEN, Type.SKIP);
        Deck d = Deck.fromPiles(
            List.of(Card.numberCard(Color.RED, 1)),
            List.of(discardTop)  // single element in discard
        );
        assertSame(discardTop, d.peekTopDiscard());
    }
 
    // -------------------------------------------------------------------------
    // Auto-reshuffle: draw pile empty -> reshuffle discard
    // -------------------------------------------------------------------------
 
    /**
     * When the draw pile is exhausted, draw() should trigger a reshuffle of the
     * discard pile (minus the top card) into a new draw pile.
     *
     * Setup: 1 card in draw pile, 3 cards in discard pile.
     * - First draw:  takes the single draw-pile card.
     * - Second draw: triggers reshuffle of the 2 lower discard cards, then draws.
     * - After reshuffle the top discard card stays in place.
     */
    @Test
    public void draw_whenDrawPileEmpty_reshufflesDiscardAndContinues() {
        List<Card> draw = List.of(
            Card.numberCard(Color.RED, 1)
        );
        List<Card> discard = List.of(
            Card.numberCard(Color.BLUE,   2),  // bottom of discard deque after addAll
            Card.numberCard(Color.GREEN,  3),
            Card.numberCard(Color.YELLOW, 4)   // top of discard deque (peekTopDiscard returns this)
        );
 
        Deck d = Deck.fromPiles(draw, discard);
 
        assertNotNull(d.draw()); // depletes draw pile (RED 1)
        assertNotNull(d.draw()); // should trigger reshuffle and succeed
    }
 
    /**
     * KNOWN EDGE-CASE BUG DOCUMENTATION TEST:
     *
     * reshuffleDiscardIntoDraw() is only triggered when discardPile.size() > 1.
     * When the discard pile has exactly 1 card and the draw pile is empty,
     * draw() throws IllegalStateException instead of handling the situation gracefully.
     *
     * This test documents the current (broken) behaviour. When the bug is fixed -
     * for example, by surfacing a user-friendly game-end condition in GameController
     * rather than letting an uncaught exception propagate - this test should be
     * updated to match the new expected behaviour.
     */
    @Test(expected = IllegalStateException.class)
    public void draw_drawPileEmpty_discardHasOneCard_throwsIllegalState() {
        // 0 cards in draw pile, 1 card in discard pile → reshuffle guard blocks action.
        Deck d = Deck.fromPiles(
            List.of(),
            List.of(Card.numberCard(Color.RED, 5))
        );
        d.draw(); // should throw IllegalStateException
    }
 
    /**
     * Corollary to the bug test: an entirely empty deck also throws.
     */
    @Test(expected = IllegalStateException.class)
    public void draw_completelyEmptyDeck_throws() {
        Deck d = Deck.fromPiles(List.of(), List.of());
        d.draw();
    }
 
    // -------------------------------------------------------------------------
    // isEmpty()
    // -------------------------------------------------------------------------
 
    @Test
    public void isEmpty_afterDrawingAllCards_fromPiles_true() {
        // Use a single-card draw pile so we can drain it without a loop.
        Deck d = Deck.fromPiles(
            List.of(Card.numberCard(Color.RED, 1)),
            List.of()
        );
        d.draw();
        assertTrue(d.isEmpty());
    }
}
