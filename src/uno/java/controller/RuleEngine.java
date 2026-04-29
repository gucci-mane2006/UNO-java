package uno.java.controller;

import java.util.*;

import uno.java.core.*;
import uno.java.player.*;

public class RuleEngine {
    static final class TurnResult {
        final int drawPenalty;
        final boolean skipNext;
        final boolean reverseDirection;
        final Color activeColor;
        final boolean awaitingColorChoice;
        final String description; // for logging

        private TurnResult(
                int drawPenalty,
                boolean skipNext,
                boolean reverseDirection,
                Color activeColor,
                boolean awaitingColorChoice,
                String description
            ) {
 
            this.drawPenalty        = drawPenalty;
            this.skipNext           = skipNext;
            this.reverseDirection   = reverseDirection;
            this.activeColor        = activeColor;
            this.awaitingColorChoice = awaitingColorChoice;
            this.description        = description;
        }

        @Override
        public String toString() { return description; }
    }

    // Number of penalty cards for failing to call UNO
    static final int UNO_PENALTY_CARDS = 2;

    TurnResult resolvePlay(Card card, GameState state) {
        if (card == null) throw new IllegalArgumentException("card cannot be null");
        if (state == null) throw new IllegalArgumentException("state cannot be null");

        return switch (card.getType()) {
            case NORMAL    -> resolveNormal(card);
            case SKIP      -> resolveSkip(card);
            case REVERSE   -> resolveReverse(card, state);
            case DRAW_TWO  -> resolveDrawTwo(card);
            case WILD      -> resolveWild();
            case DRAW_FOUR -> resolveDrawFour();
        };
    }

    // Called by controller after player has chosen a color for a wild card
    TurnResult resolveWildColor(TurnResult partial, Color chosen) {
        if (partial == null) throw new IllegalArgumentException("partial cannot be null");
        if (!partial.awaitingColorChoice)
            throw new IllegalStateException("resolveWildColor called on a non-wild TurnResult");
        if (chosen == null || chosen == Color.WILD)
            throw new IllegalArgumentException("chosen color cannot be null or WILD");
 
        String desc = partial.description + " → color set to " + chosen;
        return new TurnResult(
                partial.drawPenalty,
                partial.skipNext,
                partial.reverseDirection,
                chosen,
                false,
                desc);
    }

    /*
        INDIVIDUAL CARD RESOLVERS    
    */
    private TurnResult resolveNormal(Card card) {
        return new TurnResult(
                0, false, false,
                card.getColor(),
                false,
                "Played " + card);
    }

    private TurnResult resolveSkip(Card card) {
        return new TurnResult(
                0, true, false,
                card.getColor(),
                false,
                "Played " + card + " - next player is skipped");
    }

    private TurnResult resolveReverse(Card card, GameState state) {
        boolean twoPlayer = state.getPlayerCount() == 2;
 
        if (twoPlayer) {
            return new TurnResult(
                    0, true, false,
                    card.getColor(),
                    false,
                    "Played " + card + " (2-player: acts as Skip)");
        }
 
        return new TurnResult(
                0, false, true,
                card.getColor(),
                false,
                "Played " + card + " - direction reversed");
    }

    private TurnResult resolveDrawTwo(Card card) {
        return new TurnResult(
                2, true, false,
                card.getColor(),
                false,
                "Played " + card + " - next player draws 2 and is skipped");
    }

    private TurnResult resolveWild() {
        // activeColor is null until the controller calls resolveWildColor()
        return new TurnResult(
                0, false, false,
                null,
                true,
                "Played Wild - awaiting color choice");
    }

    private TurnResult resolveDrawFour() {
        return new TurnResult(
                4, true, false,
                null,
                true,
                "Played Wild Draw Four - awaiting color choice, next player draws 4 and is skipped");
    }

    /*
        TURN ADVANCEMENT
    */
    // the controller should call this after applying reverseDirection
    // so that getNextPlayerIndex() reflects the updated direction
    int nextPlayerIndex(TurnResult result, GameState state) {
        if (result.awaitingColorChoice)
            throw new IllegalStateException("Cannot advance turn while awaiting color choice");
 
        // skipNext means we step 2 seats instead of 1
        int steps = result.skipNext ? 2 : 1;
        return state.getNextPlayerIndex(steps);
    }

    /* 
        UNO CALL ENFORCEMENT 
    */
    boolean shouldPenaliseUno(Player player, boolean calledUno) {
        return player.getHandSize() == 1 && !calledUno;
    }

    /*
        GAME TERMINATION
    */
    boolean isGameOver(GameState state) {
        return state.getPlayers().stream().anyMatch(p -> p.getHandSize() == 0);
    }

    // returns empty if round is not over
    Optional<Player> getRoundWinner(GameState state) {
        return state.getPlayers().stream()
        .filter(p -> p.getHandSize() == 0)
        .findFirst();
    }

    /*
        FIRST CARD RULE
    */
    TurnResult resolveFirstCard(Card card, GameState state) {
        if (card == null) throw new IllegalArgumentException("card cannot be null");

        // wild draw four: reshuffle and redraw
        if (card.getType() == Type.DRAW_FOUR)
            throw new IllegalArgumentException(
            "Wild Draw Four cannot be the first card — controller must redraw"
        );

        // wild: no effect, but controller still needs a color choice
        if (card.getType() == Type.WILD) {
            return new TurnResult(
                    0, false, false,
                    null,
                    true,
                    "First card is Wild - awaiting color choice");
        }

        // All other types: resolve normally
        return resolvePlay(card, state);
    }
}
