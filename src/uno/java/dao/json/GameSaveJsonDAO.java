package uno.java.dao.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
 
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
 
import uno.java.dao.GameSaveDAO;
import uno.java.dto.GameSaveDTO;

public class GameSaveJsonDAO implements GameSaveDAO {
    private final Path saveFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
 
    public GameSaveJsonDAO(Path savesDir) {
        if (savesDir == null) throw new IllegalArgumentException("savesDir cannot be null");
        this.saveFile = savesDir.resolve("game.json");
    }
 
    // implementation
    @Override
    public void save(GameSaveDTO save) {
        if (save == null) throw new IllegalArgumentException("save cannot be null");
        try {
            Files.createDirectories(saveFile.getParent());
            Files.writeString(saveFile, gson.toJson(save));
        } catch (IOException e) {
            System.err.println("[GameSaveJsonDAO] Could not write save: " + e.getMessage());
        }
    }
 
    @Override
    public Optional<GameSaveDTO> load() {
        if (!Files.exists(saveFile)) return Optional.empty();
        try (Reader reader = Files.newBufferedReader(saveFile)) {
            GameSaveDTO dto = gson.fromJson(reader, GameSaveDTO.class);
            return Optional.ofNullable(dto);
        } catch (IOException e) {
            System.err.println("[GameSaveJsonDAO] Could not read save: " + e.getMessage());
            return Optional.empty();
        }
    }
 
    @Override
    public void delete() {
        try {
            Files.deleteIfExists(saveFile);
        } catch (IOException e) {
            System.err.println("[GameSaveJsonDAO] Could not delete save: " + e.getMessage());
        }
    }
 
    @Override
    public boolean exists() {
        return Files.exists(saveFile);
    }
}
