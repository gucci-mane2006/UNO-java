package uno.java;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class SaveLoadDialog extends JDialog {
    private final JList<String> list;
    private String selected;

    public SaveLoadDialog(Frame owner, Path saveDir) {
        super(owner, "Load Save", true);
        setSize(400, 300);
        setLocationRelativeTo(owner);

        DefaultListModel<String> model = new DefaultListModel<>();
        list = new JList<>(model);
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton load = new JButton("Load");
        JButton delete = new JButton("Delete");
        JButton close = new JButton("Close");
        btns.add(load);
        btns.add(delete);
        btns.add(close);
        add(btns, BorderLayout.SOUTH);

        List<GameSaveData> saves = GameController.listSaves(saveDir);
        for (GameSaveData s : saves) model.addElement(s.getSaveId() + "  (" + GameSaveManager.formatSaveTime(s.getSavedAt()) + ")");

        load.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) return;
            String text = model.get(idx);
            selected = text.split("\\s+")[0];
            dispose();
        });

        delete.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) return;
            String text = model.get(idx);
            String id = text.split("\\s+")[0];
            try {
                new GameSaveManager(saveDir).deleteSave(id);
                model.remove(idx);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Delete failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        close.addActionListener(e -> dispose());
    }

    public String getSelectedSaveId() { return selected; }
}
