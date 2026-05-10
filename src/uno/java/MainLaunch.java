package uno.java;

import javax.swing.*;
import java.nio.file.Path;

public class MainLaunch{
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Path saveDir = Path.of(System.getProperty("user.home"), ".uno", "saves");
            Path profilesFile = Path.of(System.getProperty("user.home"), ".uno", "profiles.json");
            Window window = new Window(saveDir, profilesFile);
            window.setVisible(true);
        });
    }
}


