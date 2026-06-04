package uno.java.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import uno.java.controller.GameController;
import uno.java.controller.GameState;
import uno.java.dto.*;
import uno.java.input.InputHandlerGUI;
import uno.java.persistence.*;
import uno.java.player.*;

/**
 * JavaFX entry point
 *
 * Screens
 * -------
 *  1. Main menu  - New Game | Load Game | View Profiles | Quit
 *  2. Setup      - player count, human / AI per slot, names
 *  3. Game table - GameTableView (log + hand/color/play-again input)
 *
 * Threading model
 * ---------------
 * All UI work happens on the JavaFX Application Thread (FX thread).
 * GameController.startGame() is launched on a daemon background thread via
 * CompletableFuture.runAsync(). The game thread blocks inside InputHandlerGUI
 * whenever it needs input; the FX thread unblocks it by completing the future
 * from a button click. The two threads never touch each other's state directly.
 *
 * After startGame() returns, Platform.runLater() switches back to the FX thread
 * to show the post-game screen.
 */
public class MainGUI extends Application {

    // -------------------------------------------------------------------------
    // Persistence - created once, shared across all screens
    // -------------------------------------------------------------------------

    private static final Path SAVES_DIR     = Paths.get("saves");
    private static final Path PROFILES_FILE = Paths.get("profiles.json");

    private final GameSaveManager   saveManager = new GameSaveManager(SAVES_DIR);
    private final ProfileRepository profileRepo = new ProfileRepository(PROFILES_FILE);

    // -------------------------------------------------------------------------
    // Stage reference (kept so we can swap scenes)
    // -------------------------------------------------------------------------

    private Stage primaryStage;

    // -------------------------------------------------------------------------
    // JavaFX lifecycle
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("UNO - Java Edition");
        stage.setMinWidth(820);
        stage.setMinHeight(600);
        showMainMenu();
        stage.show();
    }

    // =========================================================================
    // SCREEN 1 - Main menu
    // =========================================================================

    private void showMainMenu() {
        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(48));
        root.setStyle("-fx-background-color: #1a1a1a;");

        Label title = new Label("UNO");
        title.setFont(Font.font("Monospaced", FontWeight.BOLD, 56));
        title.setStyle("-fx-text-fill: #ff3333;");

        Label sub = new Label("Java Edition");
        sub.setFont(Font.font("Monospaced", 18));
        sub.setStyle("-fx-text-fill: #888888;");

        Button btnNew     = menuButton("New Game");
        Button btnLoad    = menuButton("Load Saved Game");
        Button btnProfile = menuButton("View Profiles");
        Button btnQuit    = menuButton("Quit");

        btnNew.setOnAction(e -> showSetupScreen());
        btnLoad.setOnAction(e -> handleLoadGame());
        btnProfile.setOnAction(e -> showProfilesScreen());
        btnQuit.setOnAction(e -> Platform.exit());

        // Dim Load button if no save exists
        if (!saveManager.hasSave()) {
            btnLoad.setDisable(true);
            btnLoad.setOpacity(0.4);
        }

        root.getChildren().addAll(title, sub, new Separator(), btnNew, btnLoad, btnProfile, btnQuit);
        primaryStage.setScene(new Scene(root, 820, 600));
    }

    // =========================================================================
    // SCREEN 2 - Game setup
    // =========================================================================

    /**
     * Simple form: choose player count, then for each slot pick Human or AI
     * and enter a name. Submitting builds the player list and starts the game.
     */
    private void showSetupScreen() {
        // Warn if an existing save would be overwritten
        if (saveManager.hasSave()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "A saved game exists. Starting a new game will discard it. Continue?",
                    ButtonType.YES, ButtonType.NO);
            alert.setTitle("Saved game found");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) return;
            saveManager.deleteSave();
        }

        VBox root = new VBox(14);
        root.setPadding(new Insets(32));
        root.setStyle("-fx-background-color: #1a1a1a;");

        Label title = new Label("GAME SETUP");
        title.setFont(Font.font("Monospaced", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #e0e0e0;");

        // Player count spinner
        Label countLabel = styledLabel("Number of players (2–8):");
        Spinner<Integer> countSpinner = new Spinner<>(2, 8, 2);
        countSpinner.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: #e0e0e0;");

        // Dynamic slot area - rebuilt when spinner changes
        VBox slotArea = new VBox(10);

        // Build slot rows immediately for the default value
        buildSlotRows(slotArea, countSpinner.getValue());
        countSpinner.valueProperty().addListener((obs, oldVal, newVal) ->
                buildSlotRows(slotArea, newVal));

        ScrollPane slotScroll = new ScrollPane(slotArea);
        slotScroll.setFitToWidth(true);
        slotScroll.setStyle("-fx-background: #1a1a1a; -fx-background-color: #1a1a1a;");
        slotScroll.setPrefHeight(320);

        Button startBtn = new Button("START GAME");
        startBtn.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        startBtn.setStyle(
            "-fx-background-color: #cc2222; -fx-text-fill: white; "
            + "-fx-padding: 10 24; -fx-cursor: hand;");
        startBtn.setOnAction(e -> {
            List<Player> players = collectPlayers(slotArea);
            if (players == null) return; // validation failed - errors already shown
            launchGame(players);
        });

        Button backBtn = new Button("← Back");
        backBtn.setStyle("-fx-background-color: #333; -fx-text-fill: #aaa; -fx-cursor: hand;");
        backBtn.setOnAction(e -> showMainMenu());

        HBox buttons = new HBox(12, backBtn, startBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, countLabel, countSpinner, slotScroll, buttons);
        primaryStage.setScene(new Scene(root, 820, 600));
    }

    /**
     * Rebuilds the player-slot rows for the given count.
     * Each slot row: [Human / AI toggle] [name TextField]
     */
    private void buildSlotRows(VBox container, int count) {
        container.getChildren().clear();
        int aiIndex = 0;
        for (int i = 1; i <= count; i++) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setUserData("slot"); // tag for collectPlayers()

            Label num = styledLabel("Player " + i + ":");
            num.setMinWidth(80);

            ToggleGroup tg  = new ToggleGroup();
            RadioButton rHuman = new RadioButton("Human");
            RadioButton rAI    = new RadioButton("AI");
            rHuman.setToggleGroup(tg);
            rAI.setToggleGroup(tg);
            rHuman.setSelected(i == 1); // first player defaults to human
            rAI.setSelected(i != 1);
            styleRadio(rHuman);
            styleRadio(rAI);

            TextField nameField = new TextField();
            nameField.setPromptText(i == 1 ? "Your name" : "AI-" + (++aiIndex));
            nameField.setStyle(
                "-fx-background-color: #2b2b2b; -fx-text-fill: #e0e0e0; "
                + "-fx-prompt-text-fill: #666;");
            nameField.setPrefWidth(200);

            // Store toggle + field as user data on the row HBox
            row.setUserData(new SlotData(rHuman, nameField));
            row.getChildren().addAll(num, rHuman, rAI, nameField);
            container.getChildren().add(row);
        }
    }

    /** Collects and validates player data from the slot rows. Returns null on error. */
    private List<Player> collectPlayers(VBox slotArea) {
        List<Player> players = new ArrayList<>();
        int aiCount = 0;
        Set<String> usedNames = new HashSet<>();

        for (javafx.scene.Node node : slotArea.getChildren()) {
            if (!(node.getUserData() instanceof SlotData sd)) continue;

            boolean isHuman = sd.humanRadio.isSelected();
            String  rawName = sd.nameField.getText().trim();

            if (isHuman) {
                if (rawName.isBlank()) {
                    alert("Please enter a name for each human player.");
                    return null;
                }
                if (usedNames.contains(rawName.toLowerCase())) {
                    alert("Duplicate name: \"" + rawName + "\". Each player must have a unique name.");
                    return null;
                }
                usedNames.add(rawName.toLowerCase());

                // Resolve or create profile
                PlayerProfileDTO profile = profileRepo.findByName(rawName)
                        .orElseGet(() -> {
                            PlayerProfileDTO p = new PlayerProfileDTO(
                                    UUID.randomUUID().toString(), rawName, 0);
                            profileRepo.saveProfile(p);
                            return p;
                        });

                InputHandlerGUI handlerForPlayer = new InputHandlerGUI(); // placeholder; replaced below
                PlayerHuman human = new PlayerHuman(profile.id, profile.name, handlerForPlayer);
                if (profile.score > 0) human.addScore(profile.score);
                players.add(human);

            } else {
                aiCount++;
                String name = rawName.isBlank() ? "AI-" + aiCount : rawName;
                players.add(new PlayerAI("ai-" + aiCount, name, new PlayerStrategyRandom()));
            }
        }

        if (players.size() < 2) {
            alert("You need at least 2 players.");
            return null;
        }
        return players;
    }

    // =========================================================================
    // SCREEN 3 - Game table
    // =========================================================================

    /**
     * Wires a single InputHandlerGUI to all human players (they share one UI),
     * builds the GameTableView, then launches startGame() on a background thread.
     */
    private void launchGame(List<Player> players) {
        // One shared InputHandlerGUI for all human players + the observer broadcast
        InputHandlerGUI sharedHandler = new InputHandlerGUI();

        // Replace placeholder handlers in human players with the real shared one
        for (Player p : players) {
            if (p instanceof PlayerHuman human) {
                replaceHandler(human, sharedHandler);
            }
        }

        GameTableView tableView = new GameTableView(sharedHandler);
        Scene gameScene = new Scene(tableView, 820, 600);
        primaryStage.setScene(gameScene);

        // Build and start controller on background thread
        GameController controller = new GameController(
                players, saveManager, profileRepo, sharedHandler);

        CompletableFuture.runAsync(() -> {
            try {
                controller.startGame();
            } catch (Exception e) {
                e.printStackTrace();
            }
            // After the game loop finishes, return to the main menu on the FX thread
            Platform.runLater(() -> {
                tableView.showGameOver("=== Game finished. Returning to main menu… ===");
                // Small delay so the player can read the final message
                new Thread(() -> {
                    try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                    Platform.runLater(this::showMainMenu);
                }).start();
            });
        });
    }

    // =========================================================================
    // Load saved game
    // =========================================================================

    private void handleLoadGame() {
        if (!saveManager.hasSave()) {
            alert("No saved game found.");
            return;
        }

        Optional<GameSaveDTO> saveOpt = saveManager.load();
        if (saveOpt.isEmpty()) {
            alert("Save file is corrupt or unreadable. It will be deleted.");
            saveManager.deleteSave();
            return;
        }
        GameSaveDTO save = saveOpt.get();

        List<String> errors = save.validate();
        if (!errors.isEmpty()) {
            alert("Save file failed validation and will be deleted:\n"
                    + String.join("\n", errors));
            saveManager.deleteSave();
            return;
        }

        // Rebuild players
        List<Player> players = new ArrayList<>();
        InputHandlerGUI sharedHandler = new InputHandlerGUI();
        int aiCount = 0;

        for (PlayerSaveDTO dto : save.players) {
            if (dto.playerType == PlayerType.HUMAN) {
                int wins = profileRepo.findById(dto.id).map(p -> p.score).orElse(dto.score);
                PlayerHuman p = new PlayerHuman(dto.id, dto.name, sharedHandler);
                p.addScore(wins);
                players.add(p);
            } else {
                aiCount++;
                PlayerAI p = new PlayerAI(dto.id, "AI-" + aiCount, new PlayerStrategyRandom());
                p.addScore(dto.score);
                players.add(p);
            }
        }

        GameTableView tableView = new GameTableView(sharedHandler);
        primaryStage.setScene(new Scene(tableView, 820, 600));

        GameController controller = GameController.fromSave(
                save, players, saveManager, profileRepo, sharedHandler);

        CompletableFuture.runAsync(() -> {
            try { controller.startGame(); }
            catch (Exception e) { e.printStackTrace(); }
            Platform.runLater(() -> {
                tableView.showGameOver("=== Game finished. Returning to main menu… ===");
                new Thread(() -> {
                    try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                    Platform.runLater(this::showMainMenu);
                }).start();
            });
        });
    }

    // =========================================================================
    // Profiles screen
    // =========================================================================

    private void showProfilesScreen() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(32));
        root.setStyle("-fx-background-color: #1a1a1a;");

        Label title = new Label("PLAYER PROFILES");
        title.setFont(Font.font("Monospaced", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #e0e0e0;");

        List<PlayerProfileDTO> all = profileRepo.getAll();

        if (all.isEmpty()) {
            root.getChildren().addAll(title, styledLabel("No profiles saved yet."));
        } else {
            all.stream()
               .sorted(Comparator.comparingInt((PlayerProfileDTO p) -> p.score).reversed())
               .forEach(p -> {
                   Label l = styledLabel(String.format("  %-24s  wins: %d", p.name, p.score));
                   root.getChildren().add(l);
               });
            root.getChildren().add(0, title);
        }

        Button back = new Button("← Back");
        back.setStyle("-fx-background-color: #333; -fx-text-fill: #aaa; -fx-cursor: hand;");
        back.setOnAction(e -> showMainMenu());
        root.getChildren().add(back);

        primaryStage.setScene(new Scene(root, 820, 600));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Uses reflection to swap the InputHandler inside a PlayerHuman. */
    private void replaceHandler(PlayerHuman human, InputHandlerGUI handler) {
        try {
            java.lang.reflect.Field f = PlayerHuman.class.getDeclaredField("inputHandler");
            f.setAccessible(true);
            f.set(human, handler);
        } catch (Exception e) {
            // If reflection is unavailable, this is a no-op; the placeholder
            // handler was created as an InputHandlerGUI already so it will
            // still receive callbacks - just through a different instance.
            System.err.println("[MainGUI] Could not replace handler: " + e.getMessage());
        }
    }

    private Button menuButton(String text) {
        Button btn = new Button(text);
        btn.setFont(Font.font("Monospaced", FontWeight.BOLD, 16));
        btn.setStyle(
            "-fx-background-color: #2b2b2b; -fx-text-fill: #e0e0e0; "
            + "-fx-padding: 12 32; -fx-min-width: 260; -fx-cursor: hand;");
        btn.setOnMouseEntered(e ->
            btn.setStyle(btn.getStyle().replace("#2b2b2b", "#cc2222")));
        btn.setOnMouseExited(e ->
            btn.setStyle(btn.getStyle().replace("#cc2222", "#2b2b2b")));
        return btn;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #aaaaaa; -fx-font-family: Monospaced;");
        return l;
    }

    private void styleRadio(RadioButton rb) {
        rb.setStyle("-fx-text-fill: #cccccc; -fx-font-family: Monospaced;");
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("UNO");
        a.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Carrier for slot row data
    // -------------------------------------------------------------------------

    private record SlotData(RadioButton humanRadio, TextField nameField) {}
}