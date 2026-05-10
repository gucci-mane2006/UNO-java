package uno.java;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Implements InputHandler for GUI. It exposes a method to wait for a TurnResult produced by the GUI.
 * The HandPanel will call GUIInputHandler.signalTurnResultFor(handler, result) to deliver the player's choice.
 */
public class GUIInputHandler implements InputHandler {
    private final ArrayBlockingQueue<TurnResult> queue = new ArrayBlockingQueue<>(1);
    private final MainWindow owner;

    public GUIInputHandler(MainWindow owner) {
        this.owner = owner;
    }

    @Override
    public TurnResult takeTurn(List<Card> hand, GameState state) {
        // Present a modal dialog to inform user it's their turn, then wait for queue to be signalled
        SwingUtilities.invokeLater(() -> {
            // optional: update owner UI
            owner.requestFocus();
        });

        try {
            // Wait until HandPanel calls signalTurnResultFor(...) with the player's choice.
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TurnResult.drewCard(null);
        }
    }

    @Override
    public Color selectColor() {
        Object[] opts = {"RED","YELLOW","GREEN","BLUE"};
        Object sel = javax.swing.JOptionPane.showInputDialog(null, "Choose a color:", "Wild color", javax.swing.JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (sel == null) return Color.RED;
        return Color.valueOf(sel.toString());
    }

    @Override
    public void showMessage(String message) {
        javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(owner, message));
    }

    // static helper to signal a TurnResult to a GUIInputHandler instance
    public static void signalTurnResultFor(InputHandler handler, TurnResult result) {
        if (!(handler instanceof GUIInputHandler gui)) return;
        try {
            gui.queue.clear();
            gui.queue.put(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

