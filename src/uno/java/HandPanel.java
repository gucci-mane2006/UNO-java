package uno.java;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.IntStream;

public class HandPanel extends JPanel {
    private final GameController controller;
    private final GamePanel parent;
    private final JPanel handButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private final JButton drawBtn = new JButton("Draw");
    private final JButton refreshBtn = new JButton("Refresh");

    public HandPanel(GameController controller, GamePanel parent) {
        super(new BorderLayout());
        this.controller = controller;
        this.parent = parent;

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(drawBtn);
        top.add(refreshBtn);
        add(top, BorderLayout.NORTH);

        add(new JScrollPane(handButtons), BorderLayout.CENTER);

        drawBtn.addActionListener(e -> {
            // Force current player to draw by invoking round logic: we simulate a no-play turn by calling input handler result drewCard.
            GameState state = controller.getState();
            Player current = state.getCurrentPlayer();
            if (current instanceof PlayerHuman) {
                // emulate drawing
                TurnResult res = TurnResult.drewCard(null);
                // we can't directly inject into RoundController; instead rely on controller loop to progress when player has no playable card.
                JOptionPane.showMessageDialog(this, "Draw requested — use Refresh to update after round advances.");
            } else {
                JOptionPane.showMessageDialog(this, "It's not your turn or current player is AI.");
            }
        });

        refreshBtn.addActionListener(e -> parent.refreshUI());

        updateFromState();
    }

    public void updateFromState() {
        SwingUtilities.invokeLater(() -> {
            handButtons.removeAll();
            GameState state = controller.getState();
            Player current = state.getCurrentPlayer();
            if (current == null) return;
            List<Card> hand = current.getHand();
            boolean isHuman = current instanceof PlayerHuman;
            JLabel lbl = new JLabel("Current player: " + current.getName());
            removeAll();
            add(lbl, BorderLayout.NORTH);
            JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            IntStream.range(0, hand.size()).forEach(i -> {
                Card c = hand.get(i);
                JButton b = new JButton(c.toString());
                b.setEnabled(isHuman && state.isCardPlayable(c) && current == state.getCurrentPlayer());
                b.addActionListener(e -> onPlayCard(current, c));
                center.add(b);
            });
            add(new JScrollPane(center), BorderLayout.CENTER);
            revalidate();
            repaint();
        });
    }

    private void onPlayCard(Player current, Card card) {
        if (!(current instanceof PlayerHuman human)) {
            JOptionPane.showMessageDialog(this, "Only human player can play via GUI.");
            return;
        }
        // If wild, choose color
        Color chosen = null;
        if (card.getType().isWildType()) {
            String[] opts = {"RED","YELLOW","GREEN","BLUE"};
            String sel = (String) JOptionPane.showInputDialog(this, "Choose color", "Wild color", JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
            if (sel == null) return;
            chosen = Color.valueOf(sel);
        }

        // create TurnResult and simulate applying it by interacting with state via RoundController indirectly:
        TurnResult tr = TurnResult.played(card, false, chosen);

        // The game loop operates in its own thread calling Player.takeTurn(); but here we need to cause the human player's takeTurn to return this choice.
        // The GUIInputHandler used by the PlayerHuman listens for a BlockingQueue-based request — we signal it.
        GUIInputHandler.signalTurnResultFor(human.getInputHandler(), tr);

        // Refresh UI
        parent.notifyPlayedCard(card);
    }
}

