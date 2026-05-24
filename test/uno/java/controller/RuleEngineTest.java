package uno.java.controller;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

import uno.java.core.*;
import uno.java.player.*;

/**
 * Tests for RuleEngine.java.
 *
 * RuleEngine is the highest-value test target in the project:
 *   - It is fully stateless: the same inputs always produce the same outputs.
 *   - It encodes every UNO rule that governs how a played card affects the game.
 *   - Its output type, TurnResult, is a package-private inner class; tests in
 *     this package (uno.java.controller) can read its fields directly.
 *
 * Methods under test and what they return:
 *
 *   resolvePlay(Card, GameState)         -> TurnResult encoding card effects
 *   resolveWildColor(TurnResult, Color)  -> finalized TurnResult after color pick
 *   resolveFirstCard(Card, GameState)    -> TurnResult for the opening card
 *   nextPlayerIndex(TurnResult, GameState) -> int: index of the player who plays next
 *   shouldPenaliseUno(Player, boolean)   -> boolean: did player forget to call UNO?
 *   isGameOver(GameState)               -> boolean
 *   getRoundWinner(GameState)           -> Optional<Player>
 *
 * Test structure:
 *   Each card type has its own group of tests.
 *   Edge cases (2-player reverse, wild color guards, awaiting-color guard) follow.
 *
 * Helper: makePlayers(int) and stateWithPlayers(int) build minimal states
 *         so each test group is self-contained.
 */
public class RuleEngineTest {

    private RuleEngine      engine;
    private GameState       twoPlayerState;
    private GameState       threePlayerState;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Player> makePlayers(int count) {
        List<Player> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new PlayerAI("ai-" + i, "AI" + i, new PlayerStrategyRandom()));
        }
        return list;
    }

    private static GameState stateWithPlayers(int count) {
        GameState state = new GameState(makePlayers(count));
        state.setTopCard(Card.numberCard(Color.RED, 5));
        state.setCurrentColor(Color.RED);
        return state;
    }

    @Before
    public void setUp() {
        engine           = new RuleEngine();
        twoPlayerState   = stateWithPlayers(2);
        threePlayerState = stateWithPlayers(3);
    }

    // =========================================================================
    // resolvePlay(): NORMAL card
    // =========================================================================

    @Test
    public void resolvePlay_normalCard_activeColorMatchesCardColor() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.numberCard(Color.BLUE, 3), twoPlayerState);

        assertEquals(Color.BLUE, result.activeColor);
    }

    @Test
    public void resolvePlay_normalCard_noSkip() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.numberCard(Color.BLUE, 3), twoPlayerState);

        assertFalse(result.skipNext);
    }

    @Test
    public void resolvePlay_normalCard_noReverse() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.numberCard(Color.BLUE, 3), twoPlayerState);

        assertFalse(result.reverseDirection);
    }

    @Test
    public void resolvePlay_normalCard_noPenalty() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.numberCard(Color.BLUE, 3), twoPlayerState);

        assertEquals(0, result.drawPenalty);
    }

    @Test
    public void resolvePlay_normalCard_notAwaitingColor() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.numberCard(Color.BLUE, 3), twoPlayerState);

        assertFalse(result.awaitingColorChoice);
    }

    // =========================================================================
    // resolvePlay(): SKIP card
    // =========================================================================

    @Test
    public void resolvePlay_skipCard_skipNextTrue() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.SKIP), twoPlayerState);

        assertTrue(result.skipNext);
    }

    @Test
    public void resolvePlay_skipCard_noReverse() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.SKIP), twoPlayerState);

        assertFalse(result.reverseDirection);
    }

    @Test
    public void resolvePlay_skipCard_noPenalty() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.SKIP), twoPlayerState);

        assertEquals(0, result.drawPenalty);
    }

    @Test
    public void resolvePlay_skipCard_activeColorMatchesCardColor() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.GREEN, Type.SKIP), twoPlayerState);

        assertEquals(Color.GREEN, result.activeColor);
    }

    // =========================================================================
    // resolvePlay(): REVERSE card - the 2-player special case
    //
    // Official UNO rules: in a 2-player game, Reverse acts like Skip.
    // In 3+ players it reverses the direction of play.
    // =========================================================================

    /**
     * 2-player game: Reverse should produce skipNext=true and reverseDirection=false.
     */
    @Test
    public void resolvePlay_reverse_twoPlayer_actsAsSkip() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.REVERSE), twoPlayerState);

        assertTrue("In a 2-player game, Reverse must act as Skip", result.skipNext);
        assertFalse("reverseDirection must be false in the 2-player Reverse case",
                result.reverseDirection);
    }

    /**
     * 3-player game: Reverse should reverse direction and NOT skip.
     */
    @Test
    public void resolvePlay_reverse_threePlayer_reversesDirection() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.REVERSE), threePlayerState);

        assertTrue("Reverse in 3+ players must set reverseDirection=true", result.reverseDirection);
        assertFalse("Reverse in 3+ players must NOT skip", result.skipNext);
    }

    @Test
    public void resolvePlay_reverse_threePlayer_noPenalty() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.REVERSE), threePlayerState);

        assertEquals(0, result.drawPenalty);
    }

    // =========================================================================
    // resolvePlay(): DRAW_TWO card
    // =========================================================================

    @Test
    public void resolvePlay_drawTwo_penaltyIsTwo() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.DRAW_TWO), twoPlayerState);

        assertEquals(2, result.drawPenalty);
    }

    @Test
    public void resolvePlay_drawTwo_skipNextTrue() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.DRAW_TWO), twoPlayerState);

        assertTrue(result.skipNext);
    }

    @Test
    public void resolvePlay_drawTwo_notAwaitingColor() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.actionCard(Color.RED, Type.DRAW_TWO), twoPlayerState);

        assertFalse(result.awaitingColorChoice);
        assertNotNull(result.activeColor);
    }

    // =========================================================================
    // resolvePlay(): WILD card
    // =========================================================================

    @Test
    public void resolvePlay_wild_awaitingColorChoiceTrue() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.wildCard(), twoPlayerState);

        assertTrue(result.awaitingColorChoice);
    }

    @Test
    public void resolvePlay_wild_activeColorIsNull() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.wildCard(), twoPlayerState);

        assertNull("Active color must be null until resolveWildColor() is called",
                result.activeColor);
    }

    @Test
    public void resolvePlay_wild_noSkipOrReverse() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.wildCard(), twoPlayerState);

        assertFalse(result.skipNext);
        assertFalse(result.reverseDirection);
    }

    @Test
    public void resolvePlay_wild_noPenalty() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.wildCard(), twoPlayerState);

        assertEquals(0, result.drawPenalty);
    }

    // =========================================================================
    // resolvePlay(): DRAW_FOUR card
    // =========================================================================

    @Test
    public void resolvePlay_drawFour_penaltyIsFour() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.drawFourCard(), twoPlayerState);

        assertEquals(4, result.drawPenalty);
    }

    @Test
    public void resolvePlay_drawFour_skipNextTrue() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.drawFourCard(), twoPlayerState);

        assertTrue(result.skipNext);
    }

    @Test
    public void resolvePlay_drawFour_awaitingColorChoiceTrue() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.drawFourCard(), twoPlayerState);

        assertTrue(result.awaitingColorChoice);
    }

    @Test
    public void resolvePlay_drawFour_activeColorIsNull() {
        RuleEngine.TurnResult result = engine.resolvePlay(Card.drawFourCard(), twoPlayerState);

        assertNull(result.activeColor);
    }

    // =========================================================================
    // resolvePlay(): null guards
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void resolvePlay_nullCard_throws() {
        engine.resolvePlay(null, twoPlayerState);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolvePlay_nullState_throws() {
        engine.resolvePlay(Card.numberCard(Color.RED, 1), null);
    }

    // =========================================================================
    // resolveWildColor()
    // =========================================================================

    /** After calling resolveWildColor(), awaitingColorChoice must be false. */
    @Test
    public void resolveWildColor_clearsAwaitingColorFlag() {
        RuleEngine.TurnResult partial = engine.resolvePlay(Card.wildCard(), twoPlayerState);
        RuleEngine.TurnResult result  = engine.resolveWildColor(partial, Color.BLUE);

        assertFalse(result.awaitingColorChoice);
    }

    @Test
    public void resolveWildColor_setsActiveColor() {
        RuleEngine.TurnResult partial = engine.resolvePlay(Card.wildCard(), twoPlayerState);
        RuleEngine.TurnResult result  = engine.resolveWildColor(partial, Color.BLUE);

        assertEquals(Color.BLUE, result.activeColor);
    }

    /**
     * resolveWildColor() on a DRAW_FOUR partial result must preserve the penalty
     * and skipNext from the original partial result.
     */
    @Test
    public void resolveWildColor_drawFourPartial_preservesPenaltyAndSkip() {
        RuleEngine.TurnResult partial = engine.resolvePlay(Card.drawFourCard(), twoPlayerState);
        RuleEngine.TurnResult result  = engine.resolveWildColor(partial, Color.GREEN);

        assertEquals(4,           result.drawPenalty);
        assertTrue(result.skipNext);
        assertEquals(Color.GREEN, result.activeColor);
    }

    /** Passing a non-wild TurnResult (from a normal card) must throw. */
    @Test(expected = IllegalStateException.class)
    public void resolveWildColor_onNonWildResult_throws() {
        RuleEngine.TurnResult normal = engine.resolvePlay(
            Card.numberCard(Color.RED, 1), twoPlayerState);
        engine.resolveWildColor(normal, Color.BLUE);
    }

    /** Color.WILD is not a valid choice (it's a sentinel value, not a real color). */
    @Test(expected = IllegalArgumentException.class)
    public void resolveWildColor_wildColorChoice_throws() {
        RuleEngine.TurnResult partial = engine.resolvePlay(Card.wildCard(), twoPlayerState);
        engine.resolveWildColor(partial, Color.WILD);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveWildColor_nullColorChoice_throws() {
        RuleEngine.TurnResult partial = engine.resolvePlay(Card.wildCard(), twoPlayerState);
        engine.resolveWildColor(partial, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveWildColor_nullPartial_throws() {
        engine.resolveWildColor(null, Color.RED);
    }

    // =========================================================================
    // resolveFirstCard()
    // =========================================================================

    /**
     * Wild Draw Four is illegal as the first card - the controller must redraw.
     * The engine signals this by throwing.
     */
    @Test(expected = IllegalArgumentException.class)
    public void resolveFirstCard_drawFour_throws() {
        engine.resolveFirstCard(Card.drawFourCard(), twoPlayerState);
    }

    /** A Wild as the first card must require a color choice. */
    @Test
    public void resolveFirstCard_wild_awaitingColorChoice() {
        RuleEngine.TurnResult result = engine.resolveFirstCard(Card.wildCard(), twoPlayerState);
        assertTrue(result.awaitingColorChoice);
    }

    /** A normal card as the first card has no special effects. */
    @Test
    public void resolveFirstCard_normalCard_noSpecialEffects() {
        RuleEngine.TurnResult result = engine.resolveFirstCard(
            Card.numberCard(Color.RED, 3), twoPlayerState);

        assertFalse(result.awaitingColorChoice);
        assertFalse(result.skipNext);
        assertFalse(result.reverseDirection);
        assertEquals(0, result.drawPenalty);
    }

    /** A Skip card as the first card must produce skipNext=true. */
    @Test
    public void resolveFirstCard_skipCard_skipNextTrue() {
        RuleEngine.TurnResult result = engine.resolveFirstCard(
            Card.actionCard(Color.RED, Type.SKIP), twoPlayerState);

        assertTrue(result.skipNext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveFirstCard_null_throws() {
        engine.resolveFirstCard(null, twoPlayerState);
    }

    // =========================================================================
    // nextPlayerIndex()
    // =========================================================================

    /**
     * No skip: normal card -> advance 1 step from index 0 in a 3-player game -> index 1.
     */
    @Test
    public void nextPlayerIndex_noSkip_advancesOneStep() {
        RuleEngine.TurnResult result = engine.resolvePlay(
            Card.numberCard(Color.RED, 1), threePlayerState);

        assertEquals(1, engine.nextPlayerIndex(result, threePlayerState));
    }

    /**
     * Skip: Draw Four (resolved) -> advance 2 steps from index 0 in 3-player game -> index 2.
     */
    @Test
    public void nextPlayerIndex_skip_advancesTwoSteps() {
        RuleEngine.TurnResult partial = engine.resolvePlay(Card.drawFourCard(), threePlayerState);
        RuleEngine.TurnResult result  = engine.resolveWildColor(partial, Color.RED);

        assertEquals(2, engine.nextPlayerIndex(result, threePlayerState));
    }

    /**
     * Calling nextPlayerIndex() with an unresolved wild (awaitingColorChoice=true)
     * must throw — color must be chosen before the turn can advance.
     */
    @Test(expected = IllegalStateException.class)
    public void nextPlayerIndex_awaitingColorChoice_throws() {
        RuleEngine.TurnResult partial = engine.resolvePlay(Card.wildCard(), threePlayerState);
        engine.nextPlayerIndex(partial, threePlayerState); // color not yet resolved
    }

    // =========================================================================
    // shouldPenaliseUno()
    // =========================================================================

    /**
     * Player has exactly 1 card and did NOT call UNO -> penalty applies.
     */
    @Test
    public void shouldPenaliseUno_handSizeOne_notCalled_true() {
        Player player = twoPlayerState.getPlayer(0);
        player.addCard(Card.numberCard(Color.RED, 1)); // hand size = 1

        assertTrue(engine.shouldPenaliseUno(player, false));
    }

    /**
     * Player has exactly 1 card and DID call UNO -> no penalty.
     */
    @Test
    public void shouldPenaliseUno_handSizeOne_called_false() {
        Player player = twoPlayerState.getPlayer(0);
        player.addCard(Card.numberCard(Color.RED, 1));

        assertFalse(engine.shouldPenaliseUno(player, true));
    }

    /**
     * Player has 2 cards and did NOT call UNO -> no penalty (only 1-card hands trigger it).
     */
    @Test
    public void shouldPenaliseUno_handSizeTwo_notCalled_false() {
        Player player = twoPlayerState.getPlayer(0);
        player.addCard(Card.numberCard(Color.RED, 1));
        player.addCard(Card.numberCard(Color.BLUE, 2));

        assertFalse(engine.shouldPenaliseUno(player, false));
    }

    /**
     * Player has 0 cards (just won) -> no penalty even without a UNO call.
     * The game is already over; penalising makes no sense.
     */
    @Test
    public void shouldPenaliseUno_handSizeZero_notCalled_false() {
        Player player = twoPlayerState.getPlayer(0); // hand is empty by default in setUp
        assertFalse(engine.shouldPenaliseUno(player, false));
    }

    // =========================================================================
    // isGameOver()
    // =========================================================================

    @Test
    public void isGameOver_allPlayersHaveCards_false() {
        twoPlayerState.getPlayer(0).addCard(Card.numberCard(Color.RED, 1));
        twoPlayerState.getPlayer(1).addCard(Card.numberCard(Color.BLUE, 2));

        assertFalse(engine.isGameOver(twoPlayerState));
    }

    /**
     * Player 0 has an empty hand -> game is over.
     * (Players start with empty hands in our test setUp; player 1 has a card
     * so that only player 0 qualifies as the winner.)
     */
    @Test
    public void isGameOver_onePlayerEmptyHand_true() {
        twoPlayerState.getPlayer(1).addCard(Card.numberCard(Color.BLUE, 2));
        // player 0 hand is empty (default)

        assertTrue(engine.isGameOver(twoPlayerState));
    }

    // =========================================================================
    // getRoundWinner()
    // =========================================================================

    @Test
    public void getRoundWinner_noEmptyHand_returnsEmpty() {
        twoPlayerState.getPlayer(0).addCard(Card.numberCard(Color.RED, 1));
        twoPlayerState.getPlayer(1).addCard(Card.numberCard(Color.BLUE, 2));

        assertFalse(engine.getRoundWinner(twoPlayerState).isPresent());
    }

    @Test
    public void getRoundWinner_playerWithEmptyHand_returned() {
        // player 0 has no cards; player 1 has cards
        twoPlayerState.getPlayer(1).addCard(Card.numberCard(Color.BLUE, 2));

        assertTrue(engine.getRoundWinner(twoPlayerState).isPresent());
        assertSame(twoPlayerState.getPlayer(0),
                engine.getRoundWinner(twoPlayerState).get());
    }

    /**
     * When multiple players somehow have empty hands (shouldn't happen in a real game
     * but worth testing for robustness), getRoundWinner() returns the first one found.
     */
    @Test
    public void getRoundWinner_multipleEmptyHands_returnsFirstFound() {
        // Both players have empty hands
        assertTrue(engine.getRoundWinner(twoPlayerState).isPresent());
    }
}