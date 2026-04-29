package uno.java.input;

import java.util.*;

import uno.java.controller.GameState;
import uno.java.core.*;

public class InputHandlerCUI implements InputHandler {
    private final Scanner scanner;
    private boolean unoCalled = false;

    public InputHandlerCUI(Scanner scanner) {
        if (scanner == null) throw new IllegalArgumentException("Scanner cannot be null");
        this.scanner = scanner;
    }

    // Implementation
    @Override
    public CardSelection selectCard(List<Card> hand, GameState state) {
        unoCalled = false;

        boolean hasPlayable = hand.stream().anyMatch(state::isCardPlayable);
        if (!hasPlayable) showMessage("No playable cards. Press 0 to draw.");

        displayHand(hand, state);
        showMessage("Enter card number to play, or 0 to draw.");
        showMessage("(Tip: include 'uno' in your input to call UNO - e.g. '3 uno')");

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();

            // Detect UNO call anywhere in the raw input
            if (line.toLowerCase().contains("uno")) {
                unoCalled = true;
            }

            // Strip non-digits so "3 uno", "uno3", "UNO 3" all parse to 3
            String[] parts = line.split("\\s+");
            String numberPart = "";
            for (String part : parts) {
                String digits = part.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    numberPart = digits;
                    break;
                }
            }

            if (numberPart.isEmpty()) {
                showMessage("Invalid input - enter a number, or 0 to draw.");
                continue;
            }

            int input;
            try {
                input = Integer.parseInt(numberPart);
            }
            catch (NumberFormatException e) {
                showMessage("Invalid input - enter a number, or 0 to draw.");
                continue;
            }

            if (input == 0) return new CardSelection(null, unoCalled);

            if (input < 1 || input > hand.size()) {
                showMessage("Invalid selection. Enter a number between 0 and " + hand.size() + ".");
                continue;
            }

            Card chosen = hand.get(input - 1);

            if (!state.isCardPlayable(chosen)) {
                showMessage("That card cannot be played. It must match the current "
                        + "color (" + state.getCurrentColor() + ") or type.");
                continue;
            }

            return new CardSelection(chosen, unoCalled);
        }
    }

    @Override
    public Color selectColor() {
        showMessage("Choose a color (1: Red,  2: Yellow,  3: Green,  4: Blue)");

        while (true) {
            int input = readInt();

            switch (input) {
                case 1 -> { return Color.RED; }
                case 2 -> { return Color.YELLOW; }
                case 3 -> { return Color.GREEN; }
                case 4 -> { return Color.BLUE; }
                default -> showMessage("Invalid choice. Enter 1, 2, 3, or 4.");
            }
        }
    }

    @Override
    public void showMessage(String message) {
        System.out.println(message);
    }

    // Private helpers
    private void displayHand(List<Card> hand, GameState state) {
        showMessage("\n----- Your hand -----");
        showMessage("Top card : " + formatCard(state.getTopCard())
                + "  |  Active color: " + state.getCurrentColor());
        showMessage("");

        for (int i = 0; i < hand.size(); i++) {
            Card card = hand.get(i);
            boolean playable = state.isCardPlayable(card);
            String marker = playable ? "  *" : "   ";
            showMessage(marker + (i + 1) + ". " + formatCard(card));
        }

        showMessage("");
        showMessage("  0. Draw a card");
        showMessage("  (* = playable)");
        showMessage("");
    }

    private String formatCard(Card card) {
        if (card == null) return "None";

        return switch (card.getType()) {
            case NORMAL   -> card.getColor() + " " + card.getNumber();
            case SKIP     -> card.getColor() + " Skip";
            case REVERSE  -> card.getColor() + " Reverse";
            case DRAW_TWO -> card.getColor() + " Draw Two";
            case WILD     -> "Wild";
            case DRAW_FOUR -> "Wild Draw Four";
        };
    }

    private int readInt() {
        while (true) {
            try {
                String line = scanner.nextLine().trim();
                return Integer.parseInt(line);
            } 
            catch (NumberFormatException e) {
                showMessage("Please enter a number.");
            }
        }
    }
}