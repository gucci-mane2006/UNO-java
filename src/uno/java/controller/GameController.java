package uno.java.controller;

import java.util.*;
 
import uno.java.core.*;
import uno.java.player.*;


public class GameController {
    private static final int INITIAL_HAND_SIZE = 7;

    private final List<Player> players;
    private GameState state;
    private Deck deck;
    private final RuleEngine rules   = new RuleEngine();
    private final Scanner    scanner = new Scanner(System.in);

    public GameController(List<Player> players) {
        if (players == null || players.size() < 2)
            throw new IllegalArgumentException("Game requires at least 2 players");
        this.players = new ArrayList<>(players);
    }

    /*
        PUBLIC API
    */
    public void startGame() {
        boolean playing = true;
        while (playing) {
            playRound();
 
            Optional<Player> winner = rules.getRoundWinner(state);
            winner.ifPresent(w -> {
                w.addScore(1);         // win-count only; no card-value scoring yet
                broadcast(w.getName() + " wins this round!"
                        + "(Total wins: " + w.getScore() + ")");
            });
 
            broadcast(buildScoreboard());
 
            if (!playAgain()) playing = false;
        }
    }

    public GameState getState() {
        return state;
    }

    /*
        ROUND LIFECYCLE
    */
    private void playRound() {
        for (Player p: players) {
            p.clearHand();
        }
        // Fresh state and deck for each round
        state = new GameState(players);
        deck  = new Deck();
 
        dealHands();
        setupFirstCard();
 
        broadcast("\n" + "=".repeat(50));
        broadcast("  Round " + state.getRoundNumber() + " - first card: "
                + state.getTopCard() + "  color: " + state.getCurrentColor());
        broadcast("=".repeat(50));
 
        // Main game loop
        while (!rules.isGameOver(state)) {
            playOneTurn();
        }
    }

    private void dealHands() {
        for (int i = 0; i < INITIAL_HAND_SIZE; i++) {
            for (Player p : players) {
                p.addCard(deck.draw());
            }
        }
    }

    // draws and places first card
    // redraws if +4, applies card effects
    private void setupFirstCard() {
        Card first;

        do {
            first = deck.draw();
            if (first.getType() == Type.DRAW_FOUR)
                deck.insertIntoDrawPile(first);
        }
        while (first.getType() == Type.DRAW_FOUR);

        deck.discard(first);
        state.setTopCard(first);

        RuleEngine.TurnResult result = rules.resolveFirstCard(first, state);

        // Wild first card
        if (result.awaitingColorChoice) {
            Color chosen = chooseColorFor(players.get(0));
            result = rules.resolveWildColor(result, chosen);
            broadcast("First card is Wild — " + players.get(0).getName()
                    + " chooses: " + chosen);
        }
        applyTurnResult(result);

        // skip next or reverse adjusts starting player
        if (result.reverseDirection) {
            // Direction already flipped in applyTurnResult; current index stays 0,
            // so the first real turn goes to the last player
        }

        if (result.skipNext) {
            // Advance past player 0 (who would have gone first)
            state.setCurrentPlayerIndex(state.getNextPlayerIndex(1));
        }

        if (result.drawPenalty > 0) {
            // Player 0 must draw and is skipped
            Player target = state.getCurrentPlayer();
            drawCards(target, result.drawPenalty);
            broadcast(target.getName() + " draws " + result.drawPenalty
                    + " card(s) due to the first card.");
            state.setCurrentPlayerIndex(state.getNextPlayerIndex(1));
        }
    }    

    /*
        SINGLE TURN
    */
        private void playOneTurn() {
        Player current = state.getCurrentPlayer();
 
        broadcast("\n--- " + current.getName() + "'s turn "
                + "(hand: " + current.getHandSize() + ") ---");
        broadcast("Top card: " + state.getTopCard()
                + "  |  Color: " + state.getCurrentColor());
 
        // Player chooses a card (null = draw)
        Card chosen = current.takeTurn(state);
 
        if (chosen == null) {
            // Player draws one card
            Card drawn = deck.draw();
            current.addCard(drawn);
            broadcast(current.getName() + " draws a card.");
 
            // If the drawn card is playable, play it immediately
            if (state.isCardPlayable(drawn)) {
                broadcast(current.getName() + " plays the drawn card: " + drawn);
                current.removeCard(drawn);
                checkAndApplyUnoPenalty(current);
                executePlay(current, drawn);
                return; // executePlay already advances the turn
            }
 
            // Otherwise just advance
            advanceTurn(null);
            return;
        }
 
        // Validate the selection
        if (!state.isCardPlayable(chosen)) {
            broadcast("[WARNING] " + current.getName() + " tried to play an illegal card. Drawing instead.");
            current.addCard(deck.draw());
            advanceTurn(null);
            return;
        }
 
        current.removeCard(chosen);
        broadcast(current.getName() + " plays " + chosen);
        checkAndApplyUnoPenalty(current);
        executePlay(current, chosen);
    }
        
    // Shared UNO penalty logic used by both play paths in playOneTurn()
    private void checkAndApplyUnoPenalty(Player current) {
        if (rules.shouldPenaliseUno(current, current.didCallUno())) {
            broadcast(current.getName() + " forgot to call UNO! Drawing "
                    + RuleEngine.UNO_PENALTY_CARDS + " cards.");
            drawCards(current, RuleEngine.UNO_PENALTY_CARDS);
        } else if (current.getHandSize() == 1) {
            broadcast(current.getName() + " calls UNO!");
        }
    }


    // Resolves card play: wild color select, apply result, draw penalties, turn advancement
    private void executePlay(Player player, Card card) {
        deck.discard(card);
        state.setTopCard(card);
 
        RuleEngine.TurnResult result = rules.resolvePlay(card, state);
 
        // Wild: pick a color
        if (result.awaitingColorChoice) {
            Color chosen = chooseColorFor(player);
            result = rules.resolveWildColor(result, chosen);
            broadcast(player.getName() + " chooses color: " + chosen);
        }
 
        applyTurnResult(result);
 
        // Check for win before applying draw penalty
        if (rules.isGameOver(state)) return;
 
        // Apply draw penalty to the (now-current, pre-advance) next player
        if (result.drawPenalty > 0) {
            Player target = state.getNextPlayer();
            drawCards(target, result.drawPenalty);
            broadcast(target.getName() + " draws " + result.drawPenalty + " card(s).");
        }
 
        advanceTurn(result);
    }

    /*
        HELPERS
    */
    // Applies direction reversal and color/top-card updates from a TurnResult
    private void applyTurnResult(RuleEngine.TurnResult result) {
        if (result.reverseDirection) {
            state.setClockwise(!state.isClockwise());
        }
        if (result.activeColor != null) {
            state.setCurrentColor(result.activeColor);
        }
    }

    // Advances the current player index using the rule engine
    private void advanceTurn(RuleEngine.TurnResult result) {
        if (result == null) {
            // Simple 1-step advance
            state.setCurrentPlayerIndex(state.getNextPlayerIndex(1));
            return;
        }
        int next = rules.nextPlayerIndex(result, state);
        state.setCurrentPlayerIndex(next);
    }

    // Draws {count} cards from the deck and adds them to the player's hand
    private void drawCards(Player player, int count) {
        for (int i = 0; i < count; i++) {
            player.addCard(deck.draw());
        }
    }

    // Asks the appropriate agent (human via InputHandler, AI via PlayerStrategy)
    // to choose a color after playing a wild card
    private Color chooseColorFor(Player player) {
        if (player instanceof PlayerHuman human) {
            return human.getInputHandler().selectColor();
        }
        if (player instanceof PlayerAI ai) {
            return ai.getStrategy().chooseColor(player.getHand());
        }

        // Fallback
        Color[] colors = { Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE };
        return colors[new Random().nextInt(colors.length)];
    }

    private String buildScoreboard() {
        StringBuilder sb = new StringBuilder("\n  === Scoreboard ===\n");
        players.stream()
               .sorted(Comparator.comparingInt(Player::getScore).reversed())
               .forEach(p -> sb.append("  ").append(p.getName())
                               .append(": ").append(p.getScore()).append(" win(s)\n"));
        return sb.toString();
    }
 
    private boolean playAgain() {
        broadcast("\nPlay another round? (1 = Yes, 0 = No)");
        // Find the first human player's input handler to display prompts;
        // read from our own scanner so we never close System.in
        for (Player p : players) {
            if (p instanceof PlayerHuman human) {
                while (true) {
                    System.out.print("> ");
                    String line = scanner.nextLine().trim();
                    if (line.equals("1")) return true;
                    if (line.equals("0")) return false;
                    human.getInputHandler().showMessage("Please enter 1 or 0.");
                }
            }
        }
        // All-AI game: play only one round
        return false;
    }


    private void broadcast(String message) {
        // Send to every human player's InputHandler; also print directly for AI-only games
        boolean anyHuman = false;
        for (Player p : players) {
            if (p instanceof PlayerHuman human) {
                human.getInputHandler().showMessage(message);
                anyHuman = true;
            }
        }
        if (!anyHuman) System.out.println(message);
    }

}
