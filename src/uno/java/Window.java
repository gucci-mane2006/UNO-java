package uno.java;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class Window extends JFrame {
    private final Path saveDir;
    private final Path profilesFile;

    private final Card[] placeholder = new Card[0];
    private GamePanel gamePanel;

    public Window(Path saveDir, Path profilesFile) {
        super("UNO - GUI");
        this.saveDir = saveDir;
        this.profilesFile = profilesFile;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        initMenu();
        showStartScreen();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                // optional cleanup
            }
        });
    }

    private void initMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem newGame = new JMenuItem("New Game");
        JMenuItem load = new JMenuItem("Load Save...");
        JMenuItem profiles = new JMenuItem("Profiles...");
        JMenuItem exit = new JMenuItem("Exit");

        newGame.addActionListener(e -> showNewGameDialog());
        load.addActionListener(e -> showLoadDialog());
        profiles.addActionListener(e -> showProfilesDialog());
        exit.addActionListener(e -> dispose());

        file.add(newGame);
        file.add(load);
        file.addSeparator();
        file.add(profiles);
        file.addSeparator();
        file.add(exit);
        bar.add(file);
        setJMenuBar(bar);
    }

    private void showStartScreen() {
        getContentPane().removeAll();
        JPanel p = new JPanel(new BorderLayout());
        JLabel lbl = new JLabel("<html><h1 style='text-align:center'>UNO</h1><p style='text-align:center'>Select File → New Game to begin</p></html>", SwingConstants.CENTER);
        p.add(lbl, BorderLayout.CENTER);
        getContentPane().add(p);
        revalidate();
        repaint();
    }

    private void showNewGameDialog() {
        JPanel panel = new JPanel(new BorderLayout(8,8));
        JPanel center = new JPanel(new GridLayout(0,1,4,4));
        JTextField humanName = new JTextField("You");
        JSpinner aiCount = new JSpinner(new SpinnerNumberModel(1, 1, 3, 1));
        JSpinner targetScore = new JSpinner(new SpinnerNumberModel(500, 50, 2000, 50));

        center.add(new JLabel("Human player name:"));
        center.add(humanName);
        center.add(new JLabel("AI players (1-3):"));
        center.add(aiCount);
        center.add(new JLabel("Target score:"));
        center.add(targetScore);

        panel.add(center, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(this, panel, "New Game", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String name = humanName.getText().trim();
        if (name.isEmpty()) name = "You";
        int ais = (int) aiCount.getValue();
        int target = (int) targetScore.getValue();

        // build players: human + AIs
        GUIInputHandler inputHandler = new GUIInputHandler(this);
        PlayerHuman human = new PlayerHuman(UUID.randomUUID().toString(), name, inputHandler);

        java.util.List<Player> players = new java.util.ArrayList<>();
        players.add(human);
        for (int i = 1; i <= ais; i++) {
            PlayerAI ai = new PlayerAI(UUID.randomUUID().toString(), "CPU " + i, new PlayerStrategyRandom());
            players.add(ai);
        }

        createAndStartGame(players, target);
    }

    private void showLoadDialog() {
        SaveLoadDialog dlg = new SaveLoadDialog(this, saveDir);
        dlg.setVisible(true);
        String chosen = dlg.getSelectedSaveId();
        if (chosen == null) return;

        try {
            GameController.PlayerFactory factory = psd -> {
                if (psd.isAI()) return new PlayerAI(psd.getId(), psd.getName(), new PlayerStrategyRandom());
                return new PlayerHuman(psd.getId(), psd.getName(), new GUIInputHandler(this));
            };
            GameController controller = GameController.fromSave(chosen, saveDir, profilesFile, factory);
            startGameController(controller);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load save: " + ex.getMessage(), "Load error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void showProfilesDialog() {
        ProfilesDialog dlg = new ProfilesDialog(this, profilesFile);
        dlg.setVisible(true);
    }

    private void createAndStartGame(List<Player> players, int targetScore) {
        try {
            GameController controller = new GameController(players, targetScore, saveDir, profilesFile);
            startGameController(controller);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to start game: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void startGameController(GameController controller) {
        if (gamePanel != null) {
            getContentPane().remove(gamePanel);
        }
        gamePanel = new GamePanel(controller, saveDir, profilesFile, this);
        getContentPane().add(gamePanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        gamePanel.requestFocusInWindow();
        // start game loop in background
        new Thread(() -> {
            try {
                controller.startGame();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Game finished"));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Game error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                e.printStackTrace();
            }
        }, "Game-Thread").start();
    }
}

