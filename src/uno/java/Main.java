package uno.java;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    // FILESYSTEM LAYOUT
    private static final Path SAVES_DIR     = Paths.get("saves");
     private static final Path PROFILES_FILE = Paths.get("profiles.json");
        
    // GAME CONSTANTS
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;
    private static final int DEFAULT_TARGET_SCORE = 500;
        
    // SHARED IO
    private static final Scanner scanner = new Scanner(System.in);
    
    // MAIN
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
    
    // NEW GAME
    private static void startNewGame() {
        System.out.println("\n=== NEW GAME SETUP ===");
 
        int targetScore = promptTargetScore();
        List<Player> players = promptPlayers();
 
        System.out.println("\nStarting game with " + players.size() + " players. Target score: " + targetScore + " points.");
        System.out.println("=".repeat(50));
 
        GameController controller =
                new GameController(players, targetScore, SAVES_DIR, PROFILES_FILE);
 
        offerMidGameSave(controller);      // wire up save prompt between rounds
        controller.startGame();
 
        printPostGameSummary(controller.getState());
    }
    
    // LOAD GAME
        private static void loadSavedGame() {
        System.out.println("\n=== LOAD GAME ===");
 
        List<GameSaveData> saves = GameController.listSaves(SAVES_DIR);
 
        if (saves.isEmpty()) {
            System.out.println("No save files found in '" + SAVES_DIR + "'.");
            return;
        }
 
        printSaveList(saves);
        System.out.println("  0. Back");
        System.out.println("\nEnter the number of the save to load:");
 
        int choice = readInt(0, saves.size());
        if (choice == 0) return;
 
        GameSaveData chosen = saves.get(choice - 1);
        System.out.println("\nLoading save: " + chosen.getSaveId()
                + "  (" + GameSaveManager.formatSaveTime(chosen.getSavedAt()) + ")");
 
        // The PlayerFactory reconstructs the correct Player subclass and
        // restores the per-player score that was persisted in the save file
        GameController controller = GameController.fromSave(
                chosen.getSaveId(),
                SAVES_DIR,
                PROFILES_FILE,
                psd -> buildPlayerFromSaveData(psd)
        );
 
        offerMidGameSave(controller);
        controller.startGame();
 
        printPostGameSummary(controller.getState());
    }

    // VIEW PROFILES
    private static void viewProfiles() {
        System.out.println("\n=== PLAYER PROFILES ===");
 
        ProfileRepository repo = new ProfileRepository(PROFILES_FILE);
        List<PlayerProfile> profiles = repo.listProfiles();
 
        if (profiles.isEmpty()) {
            System.out.println("No profiles found. Profiles are created automatically when a game ends.");
            return;
        }
 
        System.out.println(String.format("%-30s  %s", "Name", "Total Score"));
        System.out.println("─".repeat(42));
 
        for (PlayerProfile p : profiles) {
            System.out.println(String.format("%-30s  %d", p.getName(), p.getTotalScore()));
        }
 
        System.out.println("\nPress ENTER to continue.");
        scanner.nextLine();
    }
    
    // PLAYER SETUP HELPERS
    private static List<Player> promptPlayers() {
        // builds list of players for a new game
        
        System.out.println("\nHow many players? (" + MIN_PLAYERS + "-" + MAX_PLAYERS + ")");
        int count = readInt(MIN_PLAYERS, MAX_PLAYERS);
 
        List<Player> players = new ArrayList<>();
        int humanCount = 0;
        int aiCount    = 0;
 
        for (int i = 1; i <= count; i++) {
            System.out.println("\n--- Player " + i + " ---");
            System.out.println("  1. Human");
            System.out.println("  2. AI (random strategy)");
            System.out.println("Type:");
            int type = readInt(1, 2);
 
            if (type == 1) {
                humanCount++;
                String name = promptNonBlank("Enter name for Player " + i + ": ");
                String id   = "human-" + humanCount;
                players.add(new PlayerHuman(id, name, new InputHandlerCUI(scanner)));
            } else {
                aiCount++;
                String defaultName = "AI-" + aiCount;
                System.out.println("Enter name for AI player [" + defaultName + "]: ");
                String input = scanner.nextLine().trim();
                String name  = input.isBlank() ? defaultName : input;
                String id    = "ai-" + aiCount;
                players.add(new PlayerAI(id, name, new PlayerStrategyRandom()));
            }
        }
 
        return players;
    }
    
    private static Player buildPlayerFromSaveData(PlayerSaveData psd) {
        // rebuilds Player from save data
        
        Player player;
        if (psd.isAI()) {
            player = new PlayerAI(psd.getId(), psd.getName(), new PlayerStrategyRandom());
        } else {
            player = new PlayerHuman(psd.getId(), psd.getName(), new InputHandlerCUI(scanner));
        }
        // restore saved score
        player.addScore(psd.getScore());
        return player;
    }
    
    // MIDGAME SAVE - registers shutdown hook (ctrl+c) so player can save
    private static void offerMidGameSave(GameController controller) {
        System.out.println("(Tip: press Ctrl-C during the game to save and exit.)\n");
 
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            GameState state = controller.getState();
            // Only offer save while a round is actually in progress
            if (state.getPhase() != GamePhase.IN_PROGRESS) return;
 
            System.out.println("\n\nGame interrupted. Save progress? (y/n)");
            try {
                Scanner hookScanner = new Scanner(System.in);
                String answer = hookScanner.nextLine().trim();
                if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                    System.out.println("Enter a save name (no spaces or special characters): ");
                    String saveId = hookScanner.nextLine().trim();
                    if (!saveId.isBlank()) {
                        controller.saveGame(saveId);
                        System.out.println("Saved as '" + saveId + "' in " + SAVES_DIR + ".");
                    } else {
                        System.out.println("Save cancelled — name was blank.");
                    }
                }
            } catch (Exception e) {
                System.out.println("Could not complete save: " + e.getMessage());
            }
        }));
    }

    // POST-GAME SUMMARY
    private static void printPostGameSummary(GameState state) {
        System.out.println("\n" + "═".repeat(50));
        System.out.println("  GAME OVER");
        System.out.println("═".repeat(50));
 
        // Sort descending by score for display
        List<Player> ranked = new ArrayList<>(state.getPlayers());
        ranked.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
 
        System.out.println(String.format("  %-3s  %-25s  %s", "Pos", "Player", "Score"));
        System.out.println("  " + "─".repeat(44));
 
        for (int i = 0; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            String medal = switch (i) {
                case 0 -> "1ST PLACE";
                case 1 -> "2ND PLACE";
                case 2 -> "3RD PLACE";
                default -> "   ";
            };
            System.out.println(String.format("  %s  %-25s  %d", medal, p.getName(), p.getScore()));
        }
 
        System.out.println("═".repeat(50));
        System.out.println();
        System.out.println("Press ENTER to return to the main menu.");
        scanner.nextLine();
    }
    
    // MENUS AND BANNERS
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
    
    private static void printSaveList(List<GameSaveData> saves) {
        System.out.println(String.format("  %-4s  %-20s  %-18s  %s",
                "#", "Save ID", "Saved At", "Players"));
        System.out.println("  " + "─".repeat(60));
 
        for (int i = 0; i < saves.size(); i++) {
            GameSaveData s = saves.get(i);
            System.out.println(String.format("  %-4d  %-20s  %-18s  %d",
                    i + 1,
                    s.getSaveId(),
                    GameSaveManager.formatSaveTime(s.getSavedAt()),
                    s.getPlayers() != null ? s.getPlayers().size() : 0));
        }
    }
    
    // TARGET SCORE PROMPT
    private static int promptTargetScore() {
        System.out.println("\nTarget score to win the game? [default: " + DEFAULT_TARGET_SCORE + "]");
        System.out.println("  1.  200 points  (short game)");
        System.out.println("  2.  500 points  (standard)");
        System.out.println("  3. 1000 points  (long game)");
        System.out.println("  4. Custom");
        System.out.print("Choice [1-4, or ENTER for default]: ");
 
        String line = scanner.nextLine().trim();
 
        return switch (line) {
            case "1" -> 200;
            case "3" -> 1000;
            case "4" -> {
                System.out.println("Enter custom target score (must be > 0):");
                yield readInt(1, Integer.MAX_VALUE);
            }
            default  -> DEFAULT_TARGET_SCORE;   // covers "2", blank, and anything else
        };
    }
    
    // IO UTIL/HELPERS
    private static int readInt(int min, int max) {
        // reads an integer in (min, max) inclusive and reprompts on bad input
        
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
    
    private static String promptNonBlank(String prompt) {
        // prompts until non-blank string entered
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            if (!line.isBlank()) return line;
            System.out.println("Name cannot be blank.");
        }
    }  
}
