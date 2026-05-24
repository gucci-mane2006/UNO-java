package uno.java.core;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for Card.java.
 *
 * Card uses a private constructor; all instances are created through four static
 * factory methods: numberCard(), actionCard(), wildCard(), drawFourCard().
 * Each factory delegates to the private validateCard() method which enforces
 * the invariants listed below. These tests verify both the happy paths and
 * every rejection branch in validateCard().
 *
 * Invariants under test:
 *   - NORMAL cards must have a number in [0, 9] and a non-WILD color.
 *   - Action cards (SKIP, REVERSE, DRAW_TWO) must have a non-WILD color and no number.
 *   - WILD and DRAW_FOUR cards must have Color.WILD and no number.
 *   - No factory accepts a null color or null type.
 */
public class CardTest {
    // -------------------------------------------------------------------------
    // numberCard() factory
    // -------------------------------------------------------------------------
 
    /**
     * Baseline: all three fields should be set correctly for a valid numbered card.
     */
    @Test
    public void numberCard_validInput_fieldsSetCorrectly() {
        Card card = Card.numberCard(Color.RED, 5);
 
        assertEquals(Color.RED,      card.getColor());
        assertEquals(Type.NORMAL,    card.getType());
        assertEquals(Integer.valueOf(5), card.getNumber());
    }
 
    /** Boundary: 0 is the lowest valid number. */
    @Test
    public void numberCard_zero_isValid() {
        Card card = Card.numberCard(Color.BLUE, 0);
        assertEquals(Integer.valueOf(0), card.getNumber());
    }
 
    /** Boundary: 9 is the highest valid number. */
    @Test
    public void numberCard_nine_isValid() {
        Card card = Card.numberCard(Color.GREEN, 9);
        assertEquals(Integer.valueOf(9), card.getNumber());
    }
 
    /** Each non-WILD color must be accepted. */
    @Test
    public void numberCard_allNonWildColors_valid() {
        Color[] colors = { Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE };
        for (Color c : colors) {
            Card card = Card.numberCard(c, 1);
            assertEquals(c, card.getColor());
        }
    }
 
    @Test(expected = IllegalArgumentException.class)
    public void numberCard_nullColor_throws() {
        Card.numberCard(null, 5);
    }
 
    /** -1 is one below the valid range. */
    @Test(expected = IllegalArgumentException.class)
    public void numberCard_numberBelowRange_throws() {
        Card.numberCard(Color.RED, -1);
    }
 
    /** 10 is one above the valid range. */
    @Test(expected = IllegalArgumentException.class)
    public void numberCard_numberAboveRange_throws() {
        Card.numberCard(Color.RED, 10);
    }
    
    
    // -------------------------------------------------------------------------
    // actionCard() factory
    // -------------------------------------------------------------------------
 
    /** SKIP, REVERSE, and DRAW_TWO are the three valid action types. */
    @Test
    public void actionCard_skip_fieldsSetCorrectly() {
        Card card = Card.actionCard(Color.RED, Type.SKIP);
 
        assertEquals(Color.RED,  card.getColor());
        assertEquals(Type.SKIP,  card.getType());
        assertNull("Action cards must not have a number", card.getNumber());
    }
 
    @Test
    public void actionCard_reverse_valid() {
        Card card = Card.actionCard(Color.YELLOW, Type.REVERSE);
        assertEquals(Type.REVERSE, card.getType());
    }
 
    @Test
    public void actionCard_drawTwo_valid() {
        Card card = Card.actionCard(Color.GREEN, Type.DRAW_TWO);
        assertEquals(Type.DRAW_TWO, card.getType());
    }
 
    @Test(expected = IllegalArgumentException.class)
    public void actionCard_nullColor_throws() {
        Card.actionCard(null, Type.SKIP);
    }
 
    @Test(expected = IllegalArgumentException.class)
    public void actionCard_nullType_throws() {
        Card.actionCard(Color.RED, null);
    }
 
    /**
     * NORMAL is not an action type - validateCard() rejects it here because
     * Type.isActionType() returns false for NORMAL.
     */
    @Test(expected = IllegalArgumentException.class)
    public void actionCard_normalType_throws() {
        Card.actionCard(Color.RED, Type.NORMAL);
    }
 
    /**
     * WILD requires Color.WILD; passing it to actionCard() with a suited color
     * would violate the invariant, so the factory blocks it.
     */
    @Test(expected = IllegalArgumentException.class)
    public void actionCard_wildType_throws() {
        Card.actionCard(Color.RED, Type.WILD);
    }
 
    @Test(expected = IllegalArgumentException.class)
    public void actionCard_drawFourType_throws() {
        Card.actionCard(Color.RED, Type.DRAW_FOUR);
    }
 
    
    // -------------------------------------------------------------------------
    // wildCard() factory
    // -------------------------------------------------------------------------
 
    @Test
    public void wildCard_colorIsWild_typeIsWild_noNumber() {
        Card card = Card.wildCard();
 
        assertEquals(Color.WILD, card.getColor());
        assertEquals(Type.WILD,  card.getType());
        assertNull(card.getNumber());
    }
 
    
    // -------------------------------------------------------------------------
    // drawFourCard() factory
    // -------------------------------------------------------------------------
 
    @Test
    public void drawFourCard_colorIsWild_typeIsDrawFour_noNumber() {
        Card card = Card.drawFourCard();
 
        assertEquals(Color.WILD,      card.getColor());
        assertEquals(Type.DRAW_FOUR,  card.getType());
        assertNull(card.getNumber());
    }
 
    
    // -------------------------------------------------------------------------
    // toString()
    // -------------------------------------------------------------------------
 
    /**
     * The format for numbered cards is "[COLOR NUMBER]" - e.g. "[RED 5]".
     * This format is used in broadcast messages so it matters to gameplay output.
     */
    @Test
    public void toString_numberCard_correctFormat() {
        assertEquals("[RED 5]", Card.numberCard(Color.RED, 5).toString());
    }
 
    @Test
    public void toString_wildCard_correctFormat() {
        assertEquals("[WILD CARD]", Card.wildCard().toString());
    }
 
    @Test
    public void toString_drawFourCard_correctFormat() {
        assertEquals("[DRAW FOUR]", Card.drawFourCard().toString());
    }
 
    /**
     * Action card format is "[COLOR TYPE]" - e.g. "[BLUE SKIP]".
     */
    @Test
    public void toString_actionCard_correctFormat() {
        assertEquals("[BLUE SKIP]", Card.actionCard(Color.BLUE, Type.SKIP).toString());
    }
}
