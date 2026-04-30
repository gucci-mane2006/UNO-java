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
        CASE 1 - NEW GAME
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
        
        GameSaveDTO save = saveManager.load();
        if (save == null) {
            System.out.println("\nSave file is corrupt or unreadable. It will be deleted.");
            saveManager.deleteSave();
            return;
        }
        
        System.out.println("\n=== RESUMING SAVED GAME ===");
        System.out.println("Round: " + save.roundNumber + " | " + save.players.size() + " players");
        
        // Reconstruct player objects, restoring win counts from profiles
        List<Player> players = reconstructPlayersFromSave(save);
 
        GameController controller =
                GameController.fromSave(save, players, saveManager, profileRepo);
        controller.startGame();
 
        printPostGameSummary(controller.getState());
    }
    
    private static List<Player> reconstructPlayersFromSave(GameSaveDTO save) {
        List<Player> players    = new ArrayList<>();
        int humanCount          = 0;
        int aiCount             = 0;

        for (PlayerSaveDTO dto : save.players) {
            if ("HUMAN".equals(dto.playerType)) {
                humanCount++;
                int wins = profileRepo.findById(dto.id)
                        .map(p -> p.score)
                        .orElse(dto.score);
                PlayerHuman p = new PlayerHuman(dto.id, dto.name,
                        new InputHandlerCUI(scanner));
                p.addScore(wins);
                players.add(p);
            } else {
                aiCount++;
                PlayerAI p = new PlayerAI(dto.id, dto.name, new PlayerStrategyRandom());
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




    // POST-GAME SUMMARY
    private static void printPostGameSummary(GameState state) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("GAME OVER");
        System.out.println("=".repeat(50));
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
