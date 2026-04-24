/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uno.java;

import java.util.*;


class RuleEngine {
    private final GameState state;
    private final Deck deck;

    RuleEngine(GameState state, Deck deck) {
        this.state = Objects.requireNonNull(state);
        this.deck = Objects.requireNonNull(deck);
    }

    public Optional<Card> drawOrEndRound() {
        Optional<Card> opt = deck.drawSafe();
        if (opt.isEmpty()) {
            state.setPhase(GamePhase.ROUND_OVER);
            return Optional.empty();
        }
        return opt;
    }

    public boolean isPlayable(Card card) {
        return state.isCardPlayable(card);
    }

    public void playCard(Card card, Player player, Color chosenColor) {
        deck.discard(card);
        state.setTopCard(card);

        if (card.getType().isWildType()) {
            Color resolved = chosenColor != null ? chosenColor : resolveWildColor(player);
            state.setCurrentColor(resolved);
        } else {
            state.setCurrentColor(card.getColor());
        }

        int steps = 1;
        switch (card.getType()) {
            case SKIP -> steps = 2;
            case REVERSE -> {
                state.setClockwise(!state.isClockwise());
                steps = state.getPlayerCount() == 2 ? 2 : 1;
            }
            case DRAW_TWO -> {
                Player next = state.getNextPlayer();
                applyDraw(next, 2);
                steps = 2;
            }
            case DRAW_FOUR -> {
                Player next = state.getNextPlayer();
                applyDraw(next, 4);
                steps = 2;
            }
            default -> { }
        }

        checkUno(player);

        if (!state.isRoundOver()) state.advanceToNextPlayer(steps);
    }

    private void applyDraw(Player player, int count) {
        for (int i = 0; i < count; i++) {
            Optional<Card> drawn = deck.drawSafe();
            if (drawn.isEmpty()) throw new IllegalStateException("Draw pile exhausted while applying draw effect");
            player.addCard(drawn.get());
        }
        player.clearUnoCalledFlag();
    }

    private void checkUno(Player player) {
        if (player.getHandSize() == 1 && !player.wasUnoCalledFlag()) {
            applyDraw(player, 2);
        }
        player.clearUnoCalledFlag();
    }

    private Color resolveWildColor(Player player) {
        if (player instanceof PlayerAI ai) return ai.getStrategy().chooseColor(player.getHand());
        if (player instanceof PlayerHuman human) return human.getInputHandler().selectColor();
        throw new IllegalStateException("Unknown player type when resolving wild color");
    }
}
