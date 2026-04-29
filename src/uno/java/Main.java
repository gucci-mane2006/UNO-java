package uno.java;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import uno.java.controller.*;
import uno.java.input.*;
import uno.java.player.*;

public class Main {
    // FILESYSTEM LAYOUT
    private static final Path SAVES_DIR     = Paths.get("saves");
    private static final Path PROFILES_FILE = Paths.get("profiles.json");

    // GAME CONSTANTS
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;
        
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





    // CASE 1 -> START NEW GAME
    private static void startNewGame() {
        System.out.println("\n=== NEW GAME SETUP ===");

        List<Player> players = promptPlayers();
 
        System.out.println("\nStarting game with " + players.size() + " players.");
        System.out.println("=".repeat(50));
 
        GameController controller = new GameController(players);
 
        controller.startGame();
 
        printPostGameSummary(controller.getState());
    }





    // CASE 2 -> LOAD SAVED GAME
    private static void loadSavedGame() {
        System.out.println("PERSISTENCE NOT IMPLEMENTED YET");
    }


    

    // CASE 3 -> VIEW PROFILES
    private static void viewProfiles() {
        System.out.println("PERSISTENCE NOT IMPLEMENTED YET");
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
