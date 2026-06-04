package uno.java;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import uno.java.controller.*;
import uno.java.input.*;
import uno.java.player.*;
import uno.java.dto.*;
import uno.java.persistence.*;

public class Main {
    // FILESYSTEM LAYOUT
    private static final Path SAVES_DIR     = Paths.get("saves");
    private static final Path PROFILES_FILE = Paths.get("profiles.json");

    // GAME CONSTANTS
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;

    // SHARED IO AND PERSISTENCE - CREATED ONCE USED THROUGHOUT
    private static final Scanner            scanner     = new Scanner(System.in);
    private static final GameSaveManager    saveManager = new GameSaveManager(SAVES_DIR);
    private static final ProfileRepository  profileRepo = new ProfileRepository(PROFILES_FILE);

    /*
        MAIN - ENTRY POINT
    */

    public static void main(String[] args) {
        printBanner();
        boolean running = true;

        while (running) {
            printMainMenu();
            int choice = readInt(0, 3);

            switch (choice) {
                case 1 -> startNewGame();
                case 2 -> loadSavedGame();
                case 3 -> viewProfiles();
                case 0 -> running = false;
            }
        }

        System.out.println("\nThanks for playing UNO! Goodbye.");
        scanner.close();
    }

    /*
        CASE 1 -> NEW GAME
    */

    private static void startNewGame() {
        // Guard - if midgame save exists, ask whether to abandon it
        if (saveManager.hasSave()) {
            System.out.println("\nA saved game already exists.");
            System.out.println("Starting a new game will permanently discard it.");
            System.out.println("Abandon the saved game and start fresh? (1 = Yes, 0 = No)");
            int choice = readInt(0, 1);
            if (choice == 0) {
                System.out.println("Returning to menu.");
                return;
            }
            saveManager.deleteSave();
        }

        System.out.println("\n=== NEW GAME SETUP ===");
        List<Player> players = promptPlayers();

        System.out.println("\nStarting game with " + players.size() + " players.");
        System.out.println("=".repeat(50));

        GameController controller = new GameController(players, saveManager, profileRepo);
        controller.startGame();

        printPostGameSummary(controller.getState());
    }

    /*
        CASE 2 -> LOAD SAVED GAME
    */

    private static void loadSavedGame() {
        if (!saveManager.hasSave()) {
            System.out.println("\nNo saved game found.");
            return;
        }

        Optional<GameSaveDTO> saveOpt = saveManager.load();
        if (saveOpt.isEmpty()) {
            System.out.println("\nSave file is corrupt or unreadable. It will be deleted.");
            saveManager.deleteSave();
            return;
        }
        GameSaveDTO save = saveOpt.get();
        
        List<String> saveErrors = save.validate(); // validation call
        if (!saveErrors.isEmpty()) {
            System.out.println("\nSave file failed validation and will be deleted.");
            System.out.println("Problem(s) found:");
            saveErrors.forEach(e -> System.out.println("  - " + e));
            saveManager.deleteSave();
            return;
        }


        System.out.println("\n=== RESUMING SAVED GAME ===");
        System.out.println("Round: " + save.roundNumber + " | " + save.players.size() + " players");

        List<Player> players = reconstructPlayersFromSave(save);

        GameController controller =
                GameController.fromSave(save, players, saveManager, profileRepo);
        controller.startGame();

        printPostGameSummary(controller.getState());
    }

    /**
     * Rebuilds Player objects from a save snapshot.
     *
     * Human player IDs stored in the save are stable UUIDs (set when the game
     * was first created via promptPlayers). We look up the current score from
     * the profile rather than the save because the profile is the source of
     * truth for cumulative wins - the save only stores the score at the moment
     * the turn was written.
     *
     * If no profile is found for an ID (e.g. a save created before this fix
     * was applied, which used positional IDs), we fall back to the score stored
     * in the save file so the game can still be resumed.
     */
    private static List<Player> reconstructPlayersFromSave(GameSaveDTO save) {
        List<Player> players = new ArrayList<>();
        int aiCount = 0;

        for (PlayerSaveDTO dto : save.players) {
            if (dto.playerType == PlayerType.HUMAN) {
                int wins = profileRepo.findById(dto.id)
                        .map(p -> p.score)
                        .orElse(dto.score);
                PlayerHuman p = new PlayerHuman(dto.id, dto.name, new InputHandlerCUI(scanner));
                p.addScore(wins);
                players.add(p);
            } else {
                aiCount++;
                PlayerAI p = new PlayerAI(dto.id, "ai-" + aiCount, new PlayerStrategyRandom());
                p.addScore(dto.score);
                players.add(p);
            }
        }

        return players;
    }

    /*
        CASE 3 -> VIEW PROFILES
    */

    private static void viewProfiles() {
        List<PlayerProfileDTO> all = profileRepo.getAll();

        System.out.println("\n=== PLAYER PROFILES ===");
        if (all.isEmpty()) {
            System.out.println("  No profiles saved yet.");
            return;
        }
        all.stream()
           .sorted(Comparator.comparingInt((PlayerProfileDTO p) -> p.score).reversed())
           .forEach(p -> System.out.printf("  %-20s  wins: %d%n", p.name, p.score));
    }

    // -------------------------------------------------------------------------
    // PLAYER SETUP
    // -------------------------------------------------------------------------

    /**
     * Prompts the user to configure each player slot, then returns the
     * fully initialised player list.
     *
     * Human players are resolved against the profile store: returning players
     * get their historical win count restored; first-time players get a new
     * UUID-based profile created immediately.
     *
     * AI players retain positional IDs ("ai-1", "ai-2", …) because they
     * have no persistent profile.
     */
    private static List<Player> promptPlayers() {
        System.out.println("\nHow many players? (" + MIN_PLAYERS + "-" + MAX_PLAYERS + ")");
        int count = readInt(MIN_PLAYERS, MAX_PLAYERS);

        List<Player> players = new ArrayList<>();
        int aiCount = 0;

        for (int i = 1; i <= count; i++) {
            System.out.println("\n--- Player " + i + " ---");
            System.out.println("  1. Human");
            System.out.println("  2. AI (random strategy)");
            System.out.println("Type:");
            int type = readInt(1, 2);

            if (type == 1) {
                PlayerHuman human = promptAndResolveHumanPlayer(i, players);
                players.add(human);
            } else {
                aiCount++;
                String defaultName = "AI-" + aiCount;
                System.out.print("Enter name for AI player [" + defaultName + "]: ");
                String input = scanner.nextLine().trim();
                String name  = input.isBlank() ? defaultName : input;
                String id    = "ai-" + aiCount;
                players.add(new PlayerAI(id, name, new PlayerStrategyRandom()));
            }
        }

        return players;
    }

    /**
     * Handles the full human-player setup flow for one player slot.
     *
     * The method loops until the user provides a name that is not already
     * taken by another player in this game session. Once a valid name is
     * entered:
     *
     *   Returning player (name found in profiles):
     *     - The existing UUID is reused as the player ID.
     *     - The historical win count is seeded into the Player's in-memory
     *       score so the scoreboard and persistWin() reflect the cumulative total.
     *     - A welcome-back message is shown.
     *
     *   New player (name not found):
     *     - A fresh UUID is generated and a profile is immediately persisted
     *       so the ID is stable from the first game onwards.
     *     - The player starts with a score of zero.
     *
     * Using the profile's stored name (rather than the user's raw input) as
     * the canonical display name preserves the original capitalisation across
     * sessions (e.g. "Alice" stays "Alice" even if the user types "alice").
     *
     * @param slot     the 1-based player number, used only for the prompt label
     * @param existing players already added to this game, used for duplicate detection
     * @return a fully initialised PlayerHuman with a stable UUID and seeded score
     */
    private static PlayerHuman promptAndResolveHumanPlayer(int slot, List<Player> existing) {
        while (true) {
            String input = promptNonBlank("Enter name for Player " + slot + ": ");

            // Reject names already claimed in this session (case-insensitive)
            boolean duplicate = existing.stream()
                    .anyMatch(p -> p.getName().equalsIgnoreCase(input));
            if (duplicate) {
                System.out.println(
                        "  \"" + input + "\" is already taken in this game. Choose a different name.");
                continue;
            }

            // Look up existing profile or create a new one
            PlayerProfileDTO profile = profileRepo.findByName(input)
                    .orElseGet(() -> registerNewProfile(input));

            // Use the profile's canonical name to preserve original capitalisation
            PlayerHuman player = new PlayerHuman(
                    profile.id, profile.name, new InputHandlerCUI(scanner));

            // Seed historical wins so scoreboard and persistWin() accumulate correctly:
            //   player.score = historical wins
            //   after winning a round: player.score = historical + session wins
            //   persistWin() stores player.getScore() → correct cumulative total
            if (profile.score > 0) {
                player.addScore(profile.score);
                System.out.println(
                        "  Welcome back, " + profile.name + "!"
                        + " You have " + profile.score + " historical win(s).");
            }

            return player;
        }
    }

    /**
     * Creates a brand-new profile with a randomly generated UUID and persists
     * it immediately. Persisting upfront ensures the ID is stable even if the
     * player exits before winning a round.
     *
     * @param name the display name entered by the user
     * @return the newly created and persisted profile
     */
    private static PlayerProfileDTO registerNewProfile(String name) {
        PlayerProfileDTO profile = new PlayerProfileDTO(
                UUID.randomUUID().toString(), name, 0);
        profileRepo.saveProfile(profile);
        return profile;
    }

    // -------------------------------------------------------------------------
    // POST-GAME SUMMARY
    // -------------------------------------------------------------------------

    private static void printPostGameSummary(GameState state) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("GAME OVER");
        System.out.println("=".repeat(50));
    }

    // -------------------------------------------------------------------------
    // MENUS AND BANNERS
    // -------------------------------------------------------------------------

    private static void printBanner() {
        System.out.println("""
                ========================================
                           
                           UNO - Java Edition
                           
                ========================================
                """);
    }

    private static void printMainMenu() {
        System.out.println("=".repeat(40));
        System.out.println("  1. New Game");
        System.out.println("  2. Load Saved Game");
        System.out.println("  3. View Profiles");
        System.out.println("  0. Quit");
        System.out.println("=".repeat(40));
        System.out.print("Choice: ");
    }

    // -------------------------------------------------------------------------
    // IO UTILITIES
    // -------------------------------------------------------------------------

    /**
     * Reads an integer in [min, max] inclusive, reprompting on bad input.
     */
    private static int readInt(int min, int max) {
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= min && value <= max) return value;
                System.out.println("Please enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input - please enter a number.");
            }
        }
    }

    /**
     * Prompts until a non-blank string is entered.
     */
    private static String promptNonBlank(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            if (!line.isBlank()) return line;
            System.out.println("Name cannot be blank.");
        }
    }
}