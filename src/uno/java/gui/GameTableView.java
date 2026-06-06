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

import java.util.ArrayList;
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

    // Shared overlay layer
    private final StackPane modalOverlay  = new StackPane();
    private final StackPane modalContent  = new StackPane(); // children replaced per modal

    // ─────────────────────────────────────────────────────────────────────────
    // Log capture - used by play-again modal to extract round context
    // ─────────────────────────────────────────────────────────────────────────

    // Rolling buffer of the last N log lines; enough to always contain the
    // winner announcement + full scoreboard from GameController.broadcast()
    private static final int LOG_BUFFER  = 24;
    private final List<String> recentLogLines = new ArrayList<>(LOG_BUFFER);

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public GameTableView(InputHandlerGUI handler) {
        buildTableLayer();
        buildModalLayer();
        wireCallbacks(handler);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 0 - game table
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
    // Layer 1 - shared modal overlay
    // ─────────────────────────────────────────────────────────────────────────

    private void buildModalLayer() {
        // Scrim stretches to fill the window
        Rectangle scrim = new Rectangle();
        scrim.setFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.65));
        scrim.widthProperty().bind(widthProperty());
        scrim.heightProperty().bind(heightProperty());

        // modalContent is a plain StackPane; its one child is replaced when
        // the modal type changes (color vs play-again).
        modalContent.setMaxWidth(Region.USE_PREF_SIZE);
        modalContent.setMaxHeight(Region.USE_PREF_SIZE);

        modalOverlay.getChildren().addAll(scrim, modalContent);
        modalOverlay.setVisible(false);
        modalOverlay.setMouseTransparent(true);

        getChildren().add(modalOverlay); // Layer 1
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wire callbacks
    // ─────────────────────────────────────────────────────────────────────────

    private void wireCallbacks(InputHandlerGUI handler) {
        handler.setOnMessage(this::appendLog);
        handler.setOnCardSelected(this::showHandPanel);
        handler.setOnColorSelected(this::showColorModal);
        handler.setOnPlayAgain(this::showPlayAgainModal);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // appendLog
    // ─────────────────────────────────────────────────────────────────────────

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);

        // Keep rolling buffer for play-again context extraction
        if (recentLogLines.size() >= LOG_BUFFER) recentLogLines.remove(0);
        recentLogLines.add(message);

        // Mirror turn headers into status bar
        if (message.startsWith("---") && message.endsWith("---")) {
            statusLabel.setText(message.replace("-", "").trim());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hand panel
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
            Card card      = hand.get(i);
            boolean canPlay = state.isCardPlayable(card);
            Button btn     = cardButton(card, i + 1, canPlay);
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
    // color picker modal
    // ─────────────────────────────────────────────────────────────────────────

    private void showColorModal(CompletableFuture<uno.java.core.Color> future) {
        modalContent.getChildren().setAll(buildColorDialog(future));
        statusLabel.setText("Choose a color for your Wild card");
        fadeInModal();
    }

    private VBox buildColorDialog(CompletableFuture<uno.java.core.Color> future) {
        VBox dialog = dialogShell();

        Label header = dialogHeader("Choose a color");
        Label sub    = dialogSub("Your Wild card needs a color to continue.");
        dialog.getChildren().addAll(header, sub, new Separator());

        uno.java.core.Color[] colors = {
            uno.java.core.Color.RED,
            uno.java.core.Color.YELLOW,
            uno.java.core.Color.GREEN,
            uno.java.core.Color.BLUE
        };
        String[] bgOn  = { "#c0392b", "#d4ac0d", "#1e8449", "#1a5276" };
        String[] bgOff = { "#7b241c", "#9a7d0a", "#145a32", "#154360" };

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20, 20, 24, 20));
        grid.setHgap(12);
        grid.setVgap(12);

        for (int i = 0; i < colors.length; i++) {
            final uno.java.core.Color chosen = colors[i];
            Button swatch = buildSwatch(chosen.name(), bgOn[i], bgOff[i]);
            swatch.setOnAction(e -> dismissModal(() -> {
                statusLabel.setText("color chosen: " + chosen);
                future.complete(chosen);
            }));
            grid.add(swatch, i % 2, i / 2);
        }

        dialog.getChildren().add(grid);
        return dialog;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Play-again modal
    // ─────────────────────────────────────────────────────────────────────────

    private void showPlayAgainModal(CompletableFuture<Boolean> future) {
        modalContent.getChildren().setAll(buildPlayAgainDialog(future));
        statusLabel.setText("Round over - play again?");
        fadeInModal();
    }

    private VBox buildPlayAgainDialog(CompletableFuture<Boolean> future) {
        VBox dialog = dialogShell();
        dialog.setMaxWidth(400);

        // Winner Banner
        String winnerLine = extractWinnerLine();
        Label  winnerLabel = new Label(winnerLine.isBlank() ? "Round over!" : winnerLine);
        winnerLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 18));
        winnerLabel.setStyle(
            "-fx-text-fill: #f0c040; "         // gold
            + "-fx-font-family: Monospaced; "
            + "-fx-font-weight: bold; "
            + "-fx-font-size: 18px; "
            + "-fx-padding: 22 24 4 24;"
        );
        winnerLabel.setWrapText(true);
        winnerLabel.setMaxWidth(Double.MAX_VALUE);

        // Scoreboard
        VBox scoreBox = new VBox(4);
        scoreBox.setPadding(new Insets(8, 24, 16, 24));
        for (String line : extractScoreboardLines()) {
            Label l = new Label(line);
            l.setStyle(
                "-fx-text-fill: #cccccc; "
                + "-fx-font-family: Monospaced; "
                + "-fx-font-size: 12px;"
            );
            scoreBox.getChildren().add(l);
        }

        dialog.getChildren().addAll(winnerLabel, scoreBox, new Separator());

        // Prompt + buttons
        Label prompt = dialogSub("Play another round?");
        prompt.setPadding(new Insets(14, 24, 10, 24));

        Button yes = buildActionButton("▶  Play again",    "#1e6b3a", "#28a745");
        Button no  = buildActionButton("✕  Return to menu","#6b1e1e", "#dc3545");

        yes.setOnAction(e -> dismissModal(() -> future.complete(true)));
        no.setOnAction (e -> dismissModal(() -> future.complete(false)));

        HBox buttons = new HBox(12, yes, no);
        buttons.setPadding(new Insets(0, 20, 22, 20));
        buttons.setAlignment(Pos.CENTER);

        dialog.getChildren().addAll(prompt, buttons);
        return dialog;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Modal show / dismiss helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void fadeInModal() {
        modalOverlay.setVisible(true);
        modalOverlay.setMouseTransparent(false);
        modalOverlay.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(180), modalOverlay);
        ft.setToValue(1);
        ft.play();
    }

    private void dismissModal(Runnable onDone) {
        FadeTransition ft = new FadeTransition(Duration.millis(140), modalOverlay);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setOnFinished(e -> {
            modalOverlay.setVisible(false);
            modalOverlay.setMouseTransparent(true);
            onDone.run(); // completes future → unblocks game thread
        });
        ft.play();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Log-scraping helpers for play-again dialog context
    // ─────────────────────────────────────────────────────────────────────────

    private String extractWinnerLine() {
        for (int i = recentLogLines.size() - 1; i >= 0; i--) {
            String line = recentLogLines.get(i);
            if (line.contains("wins this round")) return line.trim();
        }
        return "";
    }

    private List<String> extractScoreboardLines() {
        List<String> result = new ArrayList<>();
        boolean inBoard = false;

        for (String line : recentLogLines) {
            if (line.contains("=== Scoreboard ===")) {
                inBoard = true;
                result.clear(); // keep only the most recent scoreboard
                continue;
            }
            if (inBoard) {
                // Scoreboard entries start with two spaces; a blank line or
                // separator marks the end of the block.
                if (line.startsWith("  ") && !line.isBlank()) {
                    result.add(line.trim());
                } else if (!line.isBlank()) {
                    inBoard = false;
                }
            }
        }
        return result;
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
    // Dialog shell builders - shared structure for both modals
    // ─────────────────────────────────────────────────────────────────────────

    private VBox dialogShell() {
        VBox box = new VBox(0);
        box.setMaxWidth(360);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        box.setAlignment(Pos.TOP_LEFT);
        box.setStyle(
            "-fx-background-color: #1e1e1e; "
            + "-fx-background-radius: 10; "
            + "-fx-border-color: #444444; "
            + "-fx-border-radius: 10; "
            + "-fx-border-width: 1; "
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 24, 0, 0, 6);"
        );
        return box;
    }

    private Label dialogHeader(String text) {
        Label l = new Label(text);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setStyle(
            "-fx-text-fill: #e0e0e0; "
            + "-fx-font-family: Monospaced; "
            + "-fx-font-weight: bold; "
            + "-fx-font-size: 17px; "
            + "-fx-padding: 22 24 8 24;"
        );
        return l;
    }

    private Label dialogSub(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setStyle(
            "-fx-text-fill: #888888; "
            + "-fx-font-family: Monospaced; "
            + "-fx-font-size: 11px; "
            + "-fx-padding: 0 24 14 24;"
        );
        return l;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Widget helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** color swatch for the color picker modal. */
    private Button buildSwatch(String label, String bgActive, String bgDim) {
        Button btn = new Button(label);
        btn.setPrefSize(130, 72);
        btn.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        String base  = "-fx-background-color: " + bgDim
                + "; -fx-text-fill: rgba(255,255,255,0.85)"
                + "; -fx-background-radius: 6; -fx-cursor: hand;";
        String hover = "-fx-background-color: " + bgActive
                + "; -fx-text-fill: #ffffff"
                + "; -fx-background-radius: 6; -fx-cursor: hand;"
                + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 2);";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited (e -> btn.setStyle(base));
        return btn;
    }

    /** Full-width action button for the play-again modal. */
    private Button buildActionButton(String label, String bgDim, String bgHover) {
        Button btn = new Button(label);
        btn.setPrefWidth(156);
        btn.setPrefHeight(44);
        btn.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
        String base  = "-fx-background-color: " + bgDim
                + "; -fx-text-fill: #dddddd"
                + "; -fx-background-radius: 6; -fx-cursor: hand;";
        String hover = "-fx-background-color: " + bgHover
                + "; -fx-text-fill: #ffffff"
                + "; -fx-background-radius: 6; -fx-cursor: hand;"
                + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 6, 0, 0, 2);";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited (e -> btn.setStyle(base));
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

    private void clearDynamicContent() {
        if (inputArea.getChildren().size() > 1)
            inputArea.getChildren().subList(1, inputArea.getChildren().size()).clear();
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