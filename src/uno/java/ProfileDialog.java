package uno.java;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class ProfilesDialog extends JDialog {
    private final ProfileRepository repo;

    public ProfilesDialog(Frame owner, Path profilesFile) {
        super(owner, "Profiles", true);
        setSize(500, 400);
        setLocationRelativeTo(owner);

        repo = new ProfileRepository(profilesFile);

        DefaultListModel<PlayerProfile> model = new DefaultListModel<>();
        JList<PlayerProfile> list = new JList<>(model);
        list.setCellRenderer((l, v, i, s, f) -> new JLabel(v.getName() + " (score: " + v.getTotalScore() + ")"));

        try {
            List<PlayerProfile> profiles = repo.listProfiles();
            profiles.forEach(model::addElement);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to load profiles: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        btns.add(close);
        add(btns, BorderLayout.SOUTH);

        close.addActionListener(e -> dispose());
    }
}

