package uno.java;

import java.util.*;

public class InputHandlerCUI implements InputHandler {
    private final Scanner scanner;

    public InputHandlerCUI(Scanner scanner) {
        if (scanner == null) throw new IllegalArgumentException("Scanner cannot be null");
        this.scanner = scanner;
    }

    // Implementation
    @Override
    public Card selectCard(List<Card> hand, GameState state) {
        displayHand(hand, state);

        while (true) {
            showMessage("Enter card number to play, or 0 to draw: ");
            int input = readInt();

            if (input == 0) return null;

            if (input < 1 || input > hand.size()) {
                showMessage("Invalid selection. Please enter a number between 0 and " + hand.size() + ".");
                continue;
            }

            Card chosen = hand.get(input - 1);

            if (!state.isCardPlayable(chosen)) {
                showMessage("That card cannot be played. It must match the current " + "color (" + state.getCurrentColor() + ") or type.");
                continue;
            }

            return chosen;
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