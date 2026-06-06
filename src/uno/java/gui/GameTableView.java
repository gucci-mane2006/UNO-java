package uno.java.gui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import uno.java.controller.GameState;
import uno.java.core.Card;
import uno.java.core.Type;
import uno.java.input.CardSelection;
import uno.java.input.InputHandlerGUI;

public class GameTableView extends StackPane {

    // ─────────────────────────────────────────────────────────────────────────
    // Permanent UI nodes
    // ─────────────────────────────────────────────────────────────────────────

    private final Label    statusLabel  = new Label("Waiting for game to start…");
    private final TextArea logArea      = new TextArea();
    private final VBox     inputArea    = new VBox(8);

    // Modal overlay - built once, shown/hidden per wild-card play
    private final StackPane modalOverlay = new StackPane();

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public GameTableView(InputHandlerGUI handler) {
        buildTableLayer();
        buildModalLayer();
        wireCallbacks(handler);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 0 - table
    // ─────────────────────────────────────────────────────────────────────────

    private void buildTableLayer() {
        BorderPane tableRoot = new BorderPane();
        tableRoot.setStyle("-fx-background-color: #1a1a1a;");

        // Status bar
        statusLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        statusLabel.setPadding(new Insets(8, 12, 8, 12));
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: #e0e0e0;");
        tableRoot.setTop(statusLabel);

        // Log area
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setFont(Font.font("Monospaced", 12));
        logArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #c8c8c8;");
        logArea.setPrefWidth(480);
        VBox logWrapper = new VBox(logArea);
        logWrapper.setPadding(new Insets(8));
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // Input side-panel
        inputArea.setPadding(new Insets(12));
        inputArea.setAlignment(Pos.TOP_CENTER);
        inputArea.setPrefWidth(300);
        inputArea.setMinWidth(260);
        inputArea.setStyle("-fx-background-color: #252525;");
        Label inputTitle = new Label("YOUR TURN");
        inputTitle.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
        inputTitle.setStyle("-fx-text-fill: #aaaaaa;");
        inputArea.getChildren().add(inputTitle);

        SplitPane split = new SplitPane(logWrapper, inputArea);
        split.setDividerPositions(0.62);
        tableRoot.setCenter(split);

        getChildren().add(tableRoot); // Layer 0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 1 - color-picker modal
    // ─────────────────────────────────────────────────────────────────────────

    private void buildModalLayer() {
        // Scrim - covers the whole table and intercepts mouse events
        Rectangle scrim = new Rectangle();
        scrim.setFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.65));
        // Bind scrim size to the StackPane so it always fills the window
        scrim.widthProperty().bind(widthProperty());
        scrim.heightProperty().bind(heightProperty());

        // Dialog card
        VBox dialog = buildColorDialog(); // populated lazily when shown

        modalOverlay.getChildren().addAll(scrim, dialog);
        modalOverlay.setVisible(false);
        modalOverlay.setMouseTransparent(true); // passthrough when hidden

        getChildren().add(modalOverlay); // Layer 1 - on top
    }

    /**
     * Builds the color-picker dialog box (static structure; the future is
     * wired each time showColorModal is called).
     *
     * Returns a VBox tagged with id "colorDialog" so showColorModal() can
     * find it and attach fresh button handlers.
     */
    private VBox buildColorDialog() {
        VBox dialog = new VBox(0);
        dialog.setId("colorDialog");
        dialog.setMaxWidth(340);
        dialog.setMaxHeight(Region.USE_PREF_SIZE);
        dialog.setAlignment(Pos.TOP_CENTER);
        dialog.setStyle(
            "-fx-background-color: #1e1e1e; "
            + "-fx-background-radius: 10; "
            + "-fx-border-color: #444444; "
            + "-fx-border-radius: 10; "
            + "-fx-border-width: 1; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 24, 0, 0, 6);"
        );

        // Header
        Label header = new Label("Choose a color");
        header.setFont(Font.font("Monospaced", FontWeight.BOLD, 17));
        header.setStyle("-fx-text-fill: #e0e0e0;");
        header.setPadding(new Insets(22, 24, 16, 24));
        header.setMaxWidth(Double.MAX_VALUE);
        header.setStyle(
            "-fx-text-fill: #e0e0e0; "
            + "-fx-font-family: Monospaced; "
            + "-fx-font-weight: bold; "
            + "-fx-font-size: 17px; "
            + "-fx-padding: 22 24 16 24;"
        );

        Label sub = new Label("Your Wild card needs a color to continue.");
        sub.setStyle(
            "-fx-text-fill: #888888; "
            + "-fx-font-family: Monospaced; "
            + "-fx-font-size: 11px; "
            + "-fx-padding: 0 24 18 24;"
        );
        sub.setWrapText(true);
        sub.setMaxWidth(Double.MAX_VALUE);

        // Divider
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #333;");

        // color swatch grid - 2×2
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20, 20, 24, 20));
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setId("colorGrid");

        uno.java.core.Color[] colors = {
            uno.java.core.Color.RED,
            uno.java.core.Color.YELLOW,
            uno.java.core.Color.GREEN,
            uno.java.core.Color.BLUE
        };
        String[] bgOn  = { "#c0392b", "#d4ac0d", "#1e8449", "#1a5276" };
        String[] bgOff = { "#7b241c", "#9a7d0a", "#145a32", "#154360" };
        String[] labels = { "RED", "YELLOW", "GREEN", "BLUE" };

        for (int i = 0; i < 4; i++) {
            Button swatch = buildSwatch(labels[i], bgOn[i], bgOff[i]);
            swatch.setId("swatch_" + colors[i].name()); // used to attach handlers
            grid.add(swatch, i % 2, i / 2);
        }

        dialog.getChildren().addAll(header, sub, sep, grid);
        return dialog;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wire callbacks
    // ─────────────────────────────────────────────────────────────────────────

    private void wireCallbacks(InputHandlerGUI handler) {
        handler.setOnMessage(this::appendLog);
        handler.setOnCardSelected(this::showHandPanel);
        handler.setOnColorSelected(this::showColorModal);
        handler.setOnPlayAgain(this::showPlayAgainPanel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // appendLog
    // ─────────────────────────────────────────────────────────────────────────

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
        if (message.startsWith("---") && message.endsWith("---")) {
            statusLabel.setText(message.replace("-", "").trim());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hand panel (unchanged from Step 1)
    // ─────────────────────────────────────────────────────────────────────────

    private void showHandPanel(InputHandlerGUI.CardSelectRequest req) {
        clearDynamicContent();
        List<Card> hand  = req.hand;
        GameState  state = req.state;

        inputArea.getChildren().add(smallLabel("Click a card to play it, or Draw to draw."));

        CheckBox unoBox = new CheckBox("Call UNO!");
        unoBox.setStyle("-fx-text-fill: #ffcc00; -fx-font-weight: bold;");
        inputArea.getChildren().add(unoBox);

        for (int i = 0; i < hand.size(); i++) {
            Card card    = hand.get(i);
            boolean canPlay = state.isCardPlayable(card);
            Button btn   = cardButton(card, i + 1, canPlay);
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

    // ─────────────────────────────────────────────────────────────────────────
    // color-picker modal
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows the modal overlay and wires each swatch button to complete the
     * future. Called on the FX thread by InputHandlerGUI.
     */
    private void showColorModal(CompletableFuture<uno.java.core.Color> future) {
        // Find the dialog VBox and its grid inside the overlay
        VBox dialog = (VBox) modalOverlay.lookup("#colorDialog");
        GridPane grid = (GridPane) dialog.lookup("#colorGrid");

        uno.java.core.Color[] colors = {
            uno.java.core.Color.RED,
            uno.java.core.Color.YELLOW,
            uno.java.core.Color.GREEN,
            uno.java.core.Color.BLUE
        };

        // Attach a fresh handler to each swatch (clears previous round's lambda)
        for (uno.java.core.Color color : colors) {
            Button swatch = (Button) grid.lookup("#swatch_" + color.name());
            if (swatch == null) continue;
            swatch.setOnAction(e -> dismissColorModal(color, future));
        }

        // Update status bar to prompt the player
        statusLabel.setText("Choose a color for your Wild card");

        // Fade the overlay in
        modalOverlay.setVisible(true);
        modalOverlay.setMouseTransparent(false);
        modalOverlay.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), modalOverlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    /**
     * Fades the modal out, then completes the future.
     * The game thread is waiting on future.join() and will proceed
     * as soon as complete() is called.
     */
    private void dismissColorModal(uno.java.core.Color chosen,
                                   CompletableFuture<uno.java.core.Color> future) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(140), modalOverlay);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            modalOverlay.setVisible(false);
            modalOverlay.setMouseTransparent(true);
            statusLabel.setText("color chosen: " + chosen);
            future.complete(chosen); // unblocks game thread
        });
        fadeOut.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Play-again panel (unchanged from Step 1)
    // ─────────────────────────────────────────────────────────────────────────

    private void showPlayAgainPanel(CompletableFuture<Boolean> future) {
        clearDynamicContent();
        inputArea.getChildren().add(smallLabel("Play another round?"));

        Button yes = new Button("Yes - play again");
        Button no  = new Button("No  - return to menu");
        styleButton(yes, "#226622", "#ffffff");
        styleButton(no,  "#662222", "#ffffff");
        yes.setMaxWidth(Double.MAX_VALUE);
        no.setMaxWidth(Double.MAX_VALUE);

        yes.setOnAction(e -> { clearDynamicContent(); future.complete(true);  });
        no.setOnAction (e -> { clearDynamicContent(); future.complete(false); });

        inputArea.getChildren().addAll(yes, no);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public updater
    // ─────────────────────────────────────────────────────────────────────────

    public void showGameOver(String summary) {
        appendLog(summary);
        clearDynamicContent();
        statusLabel.setText("Game over.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void clearDynamicContent() {
        if (inputArea.getChildren().size() > 1)
            inputArea.getChildren().subList(1, inputArea.getChildren().size()).clear();
    }

    /**
     * Builds a color swatch button for the modal grid.
     * Uses a hover effect by toggling inline style on enter/exit.
     */
    private Button buildSwatch(String label, String bgActive, String bgDim) {
        Button btn = new Button(label);
        btn.setPrefSize(130, 72);
        btn.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));

        String baseStyle =
            "-fx-background-color: " + bgDim + "; "
            + "-fx-text-fill: rgba(255,255,255,0.85); "
            + "-fx-background-radius: 6; "
            + "-fx-cursor: hand;";
        String hoverStyle =
            "-fx-background-color: " + bgActive + "; "
            + "-fx-text-fill: #ffffff; "
            + "-fx-background-radius: 6; "
            + "-fx-cursor: hand; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2);";

        btn.setStyle(baseStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e  -> btn.setStyle(baseStyle));
        return btn;
    }

    private Button cardButton(Card card, int number, boolean playable) {
        Button btn = new Button(number + ".  " + formatCard(card));
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
}