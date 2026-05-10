package uno.java;

import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;

public class TablePanel extends JPanel {
    private final GameController controller;
    private final JLabel topCardLabel = new JLabel();
    private final JLabel activeColor = new JLabel();
    private final JLabel deckInfo = new JLabel();
    private final DefaultListModel<String> playersModel = new DefaultListModel<>();
    private final JList<String> playersList = new JList<>(playersModel);

    public TablePanel(GameController controller) {
        super(new BorderLayout(6,6));
        this.controller = controller;
        JPanel top = new JPanel(new GridLayout(0,1));
        top.add(new JLabel("Top Card:"));
        top.add(topCardLabel);
        top.add(new JLabel("Active Color:"));
        top.add(activeColor);
        top.add(new JLabel("Deck / Discard:"));
        top.add(deckInfo);

        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(new JLabel("Players:"), BorderLayout.NORTH);
        playersList.setVisibleRowCount(6);
        center.add(new JScrollPane(playersList), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        updateFromState();
    }

    public void updateFromState() {
        SwingUtilities.invokeLater(() -> {
            GameState state = controller.getState();
            Card top = state.getTopCard();
            topCardLabel.setText(top != null ? top.toString() : "None");
            activeColor.setText(state.getCurrentColor() != null ? state.getCurrentColor().name() : "None");
            deckInfo.setText("Draw: " + controller.getState().getPlayers().size() + " players"); // minimal

            playersModel.clear();
            controller.getState().getPlayers().forEach(p -> {
                String s = p.getName() + "  (score: " + p.getScore() + ", cards: " + p.getHandSize() + ")";
                if (p == state.getCurrentPlayer()) s = "> " + s;
                playersModel.addElement(s);
            });
        });
    }
}

