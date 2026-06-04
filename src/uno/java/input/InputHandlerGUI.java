package uno.java.input;

import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import uno.java.controller.GameState;
import uno.java.core.Card;
import uno.java.core.Color;

/**
 * GUI implementation of InputHandler.
 *
 * Threading contract
 * ------------------
 * The game loop runs on a background thread (see MainGUI). Every method that
 * needs player input:
 *   1. Calls Platform.runLater() to push UI work onto the JavaFX Application Thread.
 *   2. Blocks the game thread on a CompletableFuture until the player acts.
 *   3. Returns the resolved value to the game thread.
 *
 * showMessage() never blocks — it just dispatches the string to the FX thread.
 *
 * The four callbacks are injected by GameTableView after the scene is built.
 */
public class InputHandlerGUI implements InputHandler {

    // -------------------------------------------------------------------------
    // Callbacks injected by the view (set once during scene construction)
    // -------------------------------------------------------------------------

    private Consumer<CardSelectRequest> onCardSelected;
    private Consumer<CompletableFuture<Color>> onColorSelected;
    private Consumer<CompletableFuture<Boolean>> onPlayAgain;
    private Consumer<String> onMessage;

    // -------------------------------------------------------------------------
    // Wiring API (called once by GameTableView)
    // -------------------------------------------------------------------------

    public void setOnCardSelected(Consumer<CardSelectRequest> h)      { this.onCardSelected  = h; }
    public void setOnColorSelected(Consumer<CompletableFuture<Color>> h) { this.onColorSelected = h; }
    public void setOnPlayAgain(Consumer<CompletableFuture<Boolean>> h) { this.onPlayAgain     = h; }
    public void setOnMessage(Consumer<String> h)                       { this.onMessage       = h; }

    // -------------------------------------------------------------------------
    // InputHandler implementation
    // -------------------------------------------------------------------------

    @Override
    public CardSelection selectCard(List<Card> hand, GameState state) {
        CompletableFuture<CardSelection> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (onCardSelected != null) onCardSelected.accept(new CardSelectRequest(hand, state, future));
        });
        return future.join(); // game thread waits; FX thread is NOT blocked
    }

    @Override
    public Color selectColor() {
        CompletableFuture<Color> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (onColorSelected != null) onColorSelected.accept(future);
        });
        return future.join();
    }

    @Override
    public void showMessage(String message) {
        // Non-blocking — just post to FX thread
        Platform.runLater(() -> {
            if (onMessage != null) onMessage.accept(message);
        });
    }

    @Override
    public boolean promptPlayAgain() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (onPlayAgain != null) onPlayAgain.accept(future);
        });
        return future.join();
    }

    // -------------------------------------------------------------------------
    // Carrier passed to the onCardSelected callback
    // -------------------------------------------------------------------------

    public static final class CardSelectRequest {
        public final List<Card>  hand;
        public final GameState   state;
        public final CompletableFuture<CardSelection> future;

        public CardSelectRequest(List<Card> hand, GameState state,
                                 CompletableFuture<CardSelection> future) {
            this.hand   = hand;
            this.state  = state;
            this.future = future;
        }
    }
}