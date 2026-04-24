/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uno.java;

import java.util.*;

class RoundController {
    private final GameState state;
    private final Deck deck;
    private final RuleEngine rules;

    RoundController(GameState state, Deck deck, RuleEngine rules) {
        this.state = Objects.requireNonNull(state);
        this.deck = Objects.requireNonNull(deck);
        this.rules = Objects.requireNonNull(rules);
    }

    public void playRound() {
        state.setPhase(GamePhase.IN_PROGRESS);
        while (!state.isRoundOver()) playTurn();
        state.setPhase(GamePhase.ROUND_OVER);
    }

    private void playTurn() {
        Player current = state.getCurrentPlayer();
        if (current == null) throw new IllegalStateException("Current player is null");

        TurnResult result = current.takeTurn(state);

        if (result != null && result.wasUnoCalled()) current.markUnoCalled();

        if (result == null || result.getPlayedCard() == null) {
            Optional<Card> drawn = rules.drawOrEndRound();
            if (drawn.isEmpty()) return; // deck exhausted -> round ended
            Card card = drawn.get();
            current.addCard(card);

            if (rules.isPlayable(card)) {
                if (!current.removeCard(card)) throw new IllegalStateException("Player attempted to play a card they don't have");
                rules.playCard(card, current, null);
            } else {
                current.clearUnoCalledFlag();
                state.advanceToNextPlayer(1);
            }
        } else {
            Card played = result.getPlayedCard();
            if (!rules.isPlayable(played)) throw new IllegalStateException("Player attempted to play an unplayable card: " + played);
            if (!current.removeCard(played)) throw new IllegalStateException("Player attempted to play a card they don't have");
            rules.playCard(played, current, result.getChosenColor());
        }
    }
}

