package uno.java.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import uno.java.controller.GameState;
import uno.java.core.Card;
import uno.java.core.Type;
import uno.java.input.CardSelection;
import uno.java.input.InputHandlerGUI;

/**
 * Step 1 stub - functional but deliberately plain.
 *
 * Layout
 * ------
 *  ┌─────────────────────────────────────────┐
 *  │  [status label - current player / turn] │
 *  ├──────────────────────┬──────────────────┤
 *  │  Message log         │  Input area      │
 *  │  (TextArea, read-    │  (dynamic -      │
 *  │   only)              │   hand buttons,  │
 *  │                      │   colour picker, │
 *  │                      │   play-again)    │
 *  └──────────────────────┴──────────────────┘
 *
 * The input area is rebuilt each time the game thread calls back into the
 * InputHandlerGUI - its content is replaced by whichever of these three
 * panels is currently needed:
 *   • Hand panel   - one button per card + a Draw button
 *   • Colour panel - four colour buttons
 *   • Play-again   - Yes / No buttons
 *
 * Threading note: every method here runs on the FX thread (called from
 * Platform.runLater in InputHandlerGUI). No CompletableFuture is touched
 * from this thread - we only ever call future.complete(), which is safe
 * from any thread.
 */
public class GameTableView extends BorderPane {

    // -------------------------------------------------------------------------
    // Shared UI nodes
    // -------------------------------------------------------------------------

    private final Label    statusLabel  = new Label("Waiting for game to start…");
    private final TextArea logArea      = new TextArea();
    private final VBox     inputArea    = new VBox(8);

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public GameTableView(InputHandlerGUI handler) {
        buildLayout();
        wireCallbacks(handler);
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private void buildLayout() {
        // Status bar at the top
        statusLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        statusLabel.setPadding(new Insets(8, 12, 8, 12));
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: #e0e0e0;");
        setTop(statusLabel);

        // Message log - left / center
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setFont(Font.font("Monospaced", 12));
        logArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #c8c8c8;");
        logArea.setPrefWidth(480);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        VBox logWrapper = new VBox(logArea);
        logWrapper.setPadding(new Insets(8));
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // Input area - right panel
        inputArea.setPadding(new Insets(12));
        inputArea.setAlignment(Pos.TOP_CENTER);
        inputArea.setPrefWidth(300);
        inputArea.setMinWidth(260);
        inputArea.setStyle("-fx-background-color: #252525;");
        Label inputTitle = new Label("YOUR TURN");
        inputTitle.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
        inputTitle.setStyle("-fx-text-fill: #aaaaaa;");
        inputArea.getChildren().add(inputTitle);

        // Split pane
        SplitPane split = new SplitPane(logWrapper, inputArea);
        split.setDividerPositions(0.62);
        setCenter(split);

        // Overall background
        setStyle("-fx-background-color: #1a1a1a;");
    }

    // -------------------------------------------------------------------------
    // Wire InputHandlerGUI callbacks
    // -------------------------------------------------------------------------

    private void wireCallbacks(InputHandlerGUI handler) {
        handler.setOnMessage(this::appendLog);
        handler.setOnCardSelected(this::showHandPanel);
        handler.setOnColorSelected(this::showColorPanel);
        handler.setOnPlayAgain(this::showPlayAgainPanel);
    }

    // -------------------------------------------------------------------------
    // appendLog - always called on FX thread via Platform.runLater
    // -------------------------------------------------------------------------

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
        // Auto-scroll
        logArea.setScrollTop(Double.MAX_VALUE);

        // Mirror turn-header lines into the status label for quick orientation
        if (message.startsWith("---") && message.endsWith("---")) {
            statusLabel.setText(message.replace("-", "").trim());
        }
    }

    // -------------------------------------------------------------------------
    // Hand panel
    // -------------------------------------------------------------------------

    private void showHandPanel(InputHandlerGUI.CardSelectRequest req) {
        // Clear all dynamic content (keep the title label at index 0)
        clearDynamicContent();

        List<Card> hand  = req.hand;
        GameState  state = req.state;

        Label hint = smallLabel("Click a card to play it, or Draw to draw.");
        inputArea.getChildren().add(hint);

        // UNO toggle
        CheckBox unoBox = new CheckBox("Call UNO!");
        unoBox.setStyle("-fx-text-fill: #ffcc00; -fx-font-weight: bold;");
        inputArea.getChildren().add(unoBox);

        // One button per card
        for (int i = 0; i < hand.size(); i++) {
            Card card     = hand.get(i);
            boolean canPlay = state.isCardPlayable(card);
            Button btn    = cardButton(card, i + 1, canPlay);

            if (canPlay) {
                final Card chosen = card;
                btn.setOnAction(e -> {
                    clearDynamicContent();
                    statusLabel.setText("Played: " + formatCard(chosen));
                    req.future.complete(new CardSelection(chosen, unoBox.isSelected()));
                });
            }
            inputArea.getChildren().add(btn);
        }

        // Draw button
        Button drawBtn = new Button("0.  Draw a card");
        styleButton(drawBtn, "#3a3a3a", "#aaaaaa");
        drawBtn.setMaxWidth(Double.MAX_VALUE);
        drawBtn.setOnAction(e -> {
            clearDynamicContent();
            statusLabel.setText("Drew a card");
            req.future.complete(new CardSelection(null, unoBox.isSelected()));
        });
        inputArea.getChildren().add(drawBtn);
    }

    // -------------------------------------------------------------------------
    // Colour picker panel
    // -------------------------------------------------------------------------

    private void showColorPanel(CompletableFuture<uno.java.core.Color> future) {
        clearDynamicContent();

        inputArea.getChildren().add(smallLabel("Choose a colour for the Wild card:"));

        uno.java.core.Color[] colours = {
            uno.java.core.Color.RED,
            uno.java.core.Color.YELLOW,
            uno.java.core.Color.GREEN,
            uno.java.core.Color.BLUE
        };
        String[] hex = { "#cc2222", "#ccaa00", "#22aa22", "#2244cc" };

        for (int i = 0; i < colours.length; i++) {
            Button btn = new Button(colours[i].name());
            styleButton(btn, hex[i], "#ffffff");
            btn.setMaxWidth(Double.MAX_VALUE);
            final uno.java.core.Color chosen = colours[i];
            btn.setOnAction(e -> {
                clearDynamicContent();
                statusLabel.setText("Colour chosen: " + chosen);
                future.complete(chosen);
            });
            inputArea.getChildren().add(btn);
        }
    }

    // -------------------------------------------------------------------------
    // Play-again panel
    // -------------------------------------------------------------------------

    private void showPlayAgainPanel(CompletableFuture<Boolean> future) {
        clearDynamicContent();

        inputArea.getChildren().add(smallLabel("Play another round?"));

        Button yes = new Button("Yes - play again");
        Button no  = new Button("No - return to menu");
        styleButton(yes, "#226622", "#ffffff");
        styleButton(no,  "#662222", "#ffffff");
        yes.setMaxWidth(Double.MAX_VALUE);
        no.setMaxWidth(Double.MAX_VALUE);

        yes.setOnAction(e -> { clearDynamicContent(); future.complete(true);  });
        no.setOnAction (e -> { clearDynamicContent(); future.complete(false); });

        inputArea.getChildren().addAll(yes, no);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Removes everything after the fixed title label (index 0). */
    private void clearDynamicContent() {
        if (inputArea.getChildren().size() > 1) {
            inputArea.getChildren().subList(1, inputArea.getChildren().size()).clear();
        }
    }

    private Button cardButton(Card card, int number, boolean playable) {
        String label = number + ".  " + formatCard(card);
        Button btn   = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);

        if (!playable) {
            styleButton(btn, "#2e2e2e", "#666666");
            btn.setDisable(true);
        } else {
            styleButton(btn, cardBgColor(card), "#ffffff");
        }
        return btn;
    }

    private void styleButton(Button btn, String bg, String fg) {
        btn.setStyle(
            "-fx-background-color: " + bg + "; "
            + "-fx-text-fill: " + fg + "; "
            + "-fx-font-family: Monospaced; "
            + "-fx-font-size: 12px; "
            + "-fx-padding: 6 10 6 10; "
            + "-fx-cursor: hand;"
        );
    }

    private String cardBgColor(Card card) {
        if (card.getType() == Type.WILD || card.getType() == Type.DRAW_FOUR) return "#555500";
        return switch (card.getColor()) {
            case RED    -> "#8b0000";
            case YELLOW -> "#8b7000";
            case GREEN  -> "#006400";
            case BLUE   -> "#00008b";
            default     -> "#444444";
        };
    }

    private Label smallLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #aaaaaa; -fx-font-family: Monospaced; -fx-font-size: 11px;");
        l.setWrapText(true);
        return l;
    }

    private String formatCard(Card card) {
        if (card == null) return "None";
        return switch (card.getType()) {
            case NORMAL    -> card.getColor() + " " + card.getNumber();
            case SKIP      -> card.getColor() + " Skip";
            case REVERSE   -> card.getColor() + " Reverse";
            case DRAW_TWO  -> card.getColor() + " Draw Two";
            case WILD      -> "Wild";
            case DRAW_FOUR -> "Wild Draw Four";
        };
    }

    // -------------------------------------------------------------------------
    // Public updater - called by MainGUI after the game ends
    // -------------------------------------------------------------------------

    public void showGameOver(String summary) {
        appendLog(summary);
        clearDynamicContent();
        statusLabel.setText("Game over.");
    }
}