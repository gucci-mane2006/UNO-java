
package uno.java.controller;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

import uno.java.core.*;
import uno.java.player.*;

/**
 * Tests for GameState.java.
 *
 * GameState is the single source of truth for all mutable in-round data.
 * Its public API includes:
 *   - isCardPlayable(Card): the most-called method in the game loop
 *   - getNextPlayerIndex(int steps): direction-aware turn advancement
 *   - Getters for current player, top card, color, direction, round number
 *
 * Its setters are package-private (accessible here because we are in the same
 * package: uno.java.controller), enforcing that only GameController mutates state.
 *
 * Test structure:
 *   - Constructor guards
 *   - isCardPlayable() - one test per branch in the method
 *   - getNextPlayerIndex() - clockwise, counter-clockwise, skip, wrap-around
 *   - Setter guard conditions (null / invalid arguments)
 *   - Round number helpers
 *
 * Helper: makePlayers(int) creates the minimum required list of PlayerAI instances
 * so tests stay focused on GameState and do not depend on player I/O.
 */
public class GameStateTest {

    // Two-player state with RED 5 on top, RED as the active color.
    private GameState twoPlayerState;

    // Three-player state - needed to distinguish Skip from Reverse behavior.
    private GameState threePlayerState;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Player> makePlayers(int count) {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            players.add(new PlayerAI("ai-" + i, "AI" + i, new PlayerStrategyRandom()));
        }
        return players;
    }

    @Before
    public void setUp() {
        twoPlayerState = new GameState(makePlayers(2));
        twoPlayerState.setTopCard(Card.numberCard(Color.RED, 5));
        twoPlayerState.setCurrentColor(Color.RED);

        threePlayerState = new GameState(makePlayers(3));
        threePlayerState.setTopCard(Card.numberCard(Color.RED, 5));
        threePlayerState.setCurrentColor(Color.RED);
    }

    // -------------------------------------------------------------------------
    // Constructor guards
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullPlayerList_throws() {
        new GameState(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_singlePlayer_throws() {
        new GameState(makePlayers(1));
    }

    @Test
    public void constructor_twoPlayers_succeeds() {
        GameState state = new GameState(makePlayers(2));
        assertEquals(2, state.getPlayerCount());
    }

    // -------------------------------------------------------------------------
    // isCardPlayable() — individual branch tests
    //
    // The method has this logic (in order):
    //   1. null card       -> false
    //   2. null top card   -> false
    //   3. WILD/DRAW_FOUR  -> true
    //   4. color match     -> true
    //   5. NORMAL vs NORMAL, same number -> true
    //   6. action vs same action type    -> true
    //   7. everything else  -> false
    // -------------------------------------------------------------------------

    /** Branch 1: null card. */
    @Test
    public void isCardPlayable_nullCard_false() {
        assertFalse(twoPlayerState.isCardPlayable(null));
    }

    /**
     * Branch 2: top card has not been set yet.
     * A freshly constructed GameState has topCard == null; no card should be playable.
     */
    @Test
    public void isCardPlayable_noTopCard_false() {
        GameState state = new GameState(makePlayers(2));
        // setCurrentColor is not called; topCard is null
        assertFalse(state.isCardPlayable(Card.numberCard(Color.RED, 1)));
    }

    /** Branch 3a: Wild is always playable regardless of top card or active color. */
    @Test
    public void isCardPlayable_wildCard_alwaysTrue() {
        assertTrue(twoPlayerState.isCardPlayable(Card.wildCard()));
    }

    /** Branch 3b: Draw Four is always playable. */
    @Test
    public void isCardPlayable_drawFour_alwaysTrue() {
        assertTrue(twoPlayerState.isCardPlayable(Card.drawFourCard()));
    }

    /** Branch 4: card color matches the active color. */
    @Test
    public void isCardPlayable_colorMatch_true() {
        // Active color is RED; RED 7 matches by color.
        assertTrue(twoPlayerState.isCardPlayable(Card.numberCard(Color.RED, 7)));
    }

    /**
     * Branch 4 negative: different color with no other match.
     * BLUE 3 vs RED 5: color mismatch, number mismatch — not playable.
     */
    @Test
    public void isCardPlayable_colorMismatch_numberMismatch_false() {
        assertFalse(twoPlayerState.isCardPlayable(Card.numberCard(Color.BLUE, 3)));
    }

    /**
     * Branch 5: two NORMAL cards, same number — playable even when colors differ.
     * Top card is RED 5; BLUE 5 matches by number.
     */
    @Test
    public void isCardPlayable_normalCard_matchingNumber_true() {
        assertTrue(twoPlayerState.isCardPlayable(Card.numberCard(Color.BLUE, 5)));
    }

    /**
     * Branch 5 negative: NORMAL card with a different number and different color.
     */
    @Test
    public void isCardPlayable_normalCard_mismatchedNumber_false() {
        assertFalse(twoPlayerState.isCardPlayable(Card.numberCard(Color.BLUE, 3)));
    }

    /**
     * Branch 5 edge: a NORMAL card vs an action-type top card.
     * isCardPlayable uses topCard.getType() == Type.NORMAL as part of the check;
     * when topCard is an action card, two normals cannot match by number.
     */
    @Test
    public void isCardPlayable_normalCard_vsActionTopCard_sameNumber_false() {
        // Top card is RED SKIP (an action type)
        twoPlayerState.setTopCard(Card.actionCard(Color.RED, Type.SKIP));
        twoPlayerState.setCurrentColor(Color.BLUE); // active color is not RED
        // YELLOW 5 - a NORMAL card played against a SKIP; branch 5 fails because
        // topCard.getType() != NORMAL, and there is no color/type match either.
        assertFalse(twoPlayerState.isCardPlayable(Card.numberCard(Color.YELLOW, 5)));
    }

    /**
     * Branch 6: action card matches by type when colors differ.
     * Top card is RED SKIP, active color is BLUE.
     * YELLOW SKIP matches by type.
     */
    @Test
    public void isCardPlayable_actionCard_matchingType_true() {
        twoPlayerState.setTopCard(Card.actionCard(Color.RED, Type.SKIP));
        twoPlayerState.setCurrentColor(Color.BLUE);

        assertTrue(twoPlayerState.isCardPlayable(Card.actionCard(Color.YELLOW, Type.SKIP)));
    }

    /**
     * Branch 6 negative: different action type, different color.
     * Top card RED SKIP, active color RED; BLUE REVERSE: color mismatch, type mismatch.
     */
    @Test
    public void isCardPlayable_actionCard_mismatchedTypeAndColor_false() {
        twoPlayerState.setTopCard(Card.actionCard(Color.RED, Type.SKIP));
        twoPlayerState.setCurrentColor(Color.RED);

        assertFalse(twoPlayerState.isCardPlayable(Card.actionCard(Color.BLUE, Type.REVERSE)));
    }

    /**
     * Action card matching active color (not type): covered by branch 4,
     * confirmed here with an action card explicitly.
     */
    @Test
    public void isCardPlayable_actionCard_matchingColor_true() {
        // Active color is RED; RED REVERSE matches by color (branch 4).
        assertTrue(twoPlayerState.isCardPlayable(Card.actionCard(Color.RED, Type.REVERSE)));
    }

    // -------------------------------------------------------------------------
    // getNextPlayerIndex(int steps)
    // -------------------------------------------------------------------------

    /**
     * Clockwise, index 0, 3 players, 1 step -> index 1.
     */
    @Test
    public void getNextPlayerIndex_clockwise_oneStep() {
        assertEquals(1, threePlayerState.getNextPlayerIndex(1));
    }

    /**
     * Clockwise wrap-around: index 2 (last), 3 players, 1 step -> index 0.
     */
    @Test
    public void getNextPlayerIndex_clockwise_wrapsAround() {
        threePlayerState.setCurrentPlayerIndex(2);
        assertEquals(0, threePlayerState.getNextPlayerIndex(1));
    }

    /**
     * Counter-clockwise, index 0, 3 players, 1 step -> index 2 (wraps).
     */
    @Test
    public void getNextPlayerIndex_counterClockwise_wrapsAround() {
        threePlayerState.setClockwise(false);
        assertEquals(2, threePlayerState.getNextPlayerIndex(1));
    }

    /**
     * Counter-clockwise, index 2, 3 players, 1 step -> index 1.
     */
    @Test
    public void getNextPlayerIndex_counterClockwise_normalStep() {
        threePlayerState.setCurrentPlayerIndex(2);
        threePlayerState.setClockwise(false);
        assertEquals(1, threePlayerState.getNextPlayerIndex(1));
    }

    /**
     * Skip scenario (steps = 2): clockwise from index 0 in 3-player game -> index 2.
     */
    @Test
    public void getNextPlayerIndex_skip_advancesByTwo() {
        assertEquals(2, threePlayerState.getNextPlayerIndex(2));
    }

    /**
     * Skip wrap-around: index 1, 3 players, clockwise, 2 steps -> index 0.
     */
    @Test
    public void getNextPlayerIndex_skip_wrapsAround() {
        threePlayerState.setCurrentPlayerIndex(1);
        assertEquals(0, threePlayerState.getNextPlayerIndex(2));
    }

    // -------------------------------------------------------------------------
    // Setter guard conditions
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void setCurrentColor_null_throws() {
        twoPlayerState.setCurrentColor(null);
    }

    /**
     * Color.WILD is a sentinel used on Card objects; it must never be the active
     * game color because isCardPlayable() uses currentColor for comparisons.
     */
    @Test(expected = IllegalArgumentException.class)
    public void setCurrentColor_wildColor_throws() {
        twoPlayerState.setCurrentColor(Color.WILD);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setTopCard_null_throws() {
        twoPlayerState.setTopCard(null);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setCurrentPlayerIndex_tooHigh_throws() {
        twoPlayerState.setCurrentPlayerIndex(99);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setCurrentPlayerIndex_negative_throws() {
        twoPlayerState.setCurrentPlayerIndex(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getPlayer_negativeIndex_throws() {
        twoPlayerState.getPlayer(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getPlayer_indexTooHigh_throws() {
        twoPlayerState.getPlayer(99);
    }

    // -------------------------------------------------------------------------
    // Round number helpers
    // -------------------------------------------------------------------------

    @Test
    public void getRoundNumber_initiallyOne() {
        assertEquals(1, twoPlayerState.getRoundNumber());
    }

    @Test
    public void incrementRound_increasesRoundNumberByOne() {
        twoPlayerState.incrementRound();
        assertEquals(2, twoPlayerState.getRoundNumber());
    }

    @Test
    public void incrementRound_calledTwice_roundNumberIsThree() {
        twoPlayerState.incrementRound();
        twoPlayerState.incrementRound();
        assertEquals(3, twoPlayerState.getRoundNumber());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setRoundNumber_zero_throws() {
        twoPlayerState.setRoundNumber(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setRoundNumber_negative_throws() {
        twoPlayerState.setRoundNumber(-5);
    }

    @Test
    public void setRoundNumber_one_valid() {
        twoPlayerState.setRoundNumber(1);
        assertEquals(1, twoPlayerState.getRoundNumber());
    }

    // -------------------------------------------------------------------------
    // Miscellaneous getters
    // -------------------------------------------------------------------------

    @Test
    public void getPlayers_returnsUnmodifiableView() {
        List<Player> players = twoPlayerState.getPlayers();
        try {
            players.add(new PlayerAI("x", "X", new PlayerStrategyRandom()));
            fail("Expected UnsupportedOperationException from unmodifiable list");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void isClockwise_defaultIsTrue() {
        assertTrue(twoPlayerState.isClockwise());
    }

    @Test
    public void getNextPlayer_returnsPlayerAtNextIndex() {
        Player expected = twoPlayerState.getPlayer(1);
        assertSame(expected, twoPlayerState.getNextPlayer());
    }
}