package uno.java;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;

public class GamePanel extends JPanel {
    private final GameController controller;
    private final Path saveDir;
    private final Path profilesFile;
    private final MainWindow parent;

    private final TablePanel tablePanel;
    private final HandPanel handPanel;
    private final JLabel statusLabel;
    private final JButton saveBtn;
    private final JButton callUnoBtn;
    private final JButton endTurnBtn;

    public GamePanel(GameController controller, Path saveDir, Path profilesFile, MainWindow parent) {
        super(new BorderLayout(8,8));
        this.controller = controller;
        this.saveDir = saveDir;
        this.profilesFile = profilesFile;
        this.parent = parent;

        tablePanel = new TablePanel(controller);
        handPanel = new HandPanel(controller, this);

        JPanel right = new JPanel(new BorderLayout(4,4));
        statusLabel = new JLabel("Round: 1");
        JPanel buttons = new JPanel(new GridLayout(0,1,4,4));
        saveBtn = new JButton("Save Game");
        callUnoBtn = new JButton("Call UNO");
        endTurnBtn = new JButton("Refresh");

        saveBtn.addActionListener(this::onSave);
        callUnoBtn.addActionListener(e -> controller.callUno(controller.getState().getCurrentPlayer()));
        endTurnBtn.addActionListener(e -> refreshUI());

        buttons.add(saveBtn);
        buttons.add(callUnoBtn);
        buttons.add(endTurnBtn);

        right.add(statusLabel, BorderLayout.NORTH);
        right.add(buttons, BorderLayout.CENTER);

        add(tablePanel, BorderLayout.CENTER);
        add(handPanel, BorderLayout.SOUTH);
        add(right, BorderLayout.EAST);

        // timer to refresh UI periodically
        Timer t = new Timer(500, e -> refreshUI());
        t.start();
    }

    public void refreshUI() {
        SwingUtilities.invokeLater(() -> {
            GameState state = controller.getState();
            statusLabel.setText("Phase: " + state.getPhase() + " | Round: " + state.getRoundNumber()
                    + " | Current: " + state.getCurrentPlayer().getName());
            tablePanel.updateFromState();
            handPanel.updateFromState();
        });
    }

    private void onSave(ActionEvent e) {
        String saveId = JOptionPane.showInputDialog(this, "Save id (simple name):", "save1");
        if (saveId == null || saveId.isBlank()) return;
        try {
            controller.saveGame(saveId.trim());
            JOptionPane.showMessageDialog(this, "Saved as: " + saveId);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void notifyPlayedCard(Card card) {
        // called by HandPanel when player plays a card via GUI — perform action on controller's state via input handler.
        refreshUI();
    }
}
