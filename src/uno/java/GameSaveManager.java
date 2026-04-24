package uno.java;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
 
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


public class GameSaveManager {
    private static final String SAVE_PREFIX    = "save_";
    private static final String SAVE_EXTENSION = ".json";
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
 
    private final Path saveDirectory;
    private final Gson gson;
    
    // Constructor
    public GameSaveManager(Path saveDirectory) {
        if (saveDirectory == null) throw new IllegalArgumentException("Save directory cannot be null");
 
        this.saveDirectory = saveDirectory;
        this.gson          = new GsonBuilder().setPrettyPrinting().create();
    }

    /*
        PUBLIC API
    */   
    public void saveGame(String saveId, GameState state) {
        validateSaveId(saveId);
        if (state == null) throw new IllegalArgumentException("GameState cannot be null");
 
        ensureDirectoryExists();
 
        List<PlayerSaveData> playerSnapshots = state.getPlayers()
                .stream()
                .map(PlayerSaveData::from)
                .toList();
 
        GameSaveData saveData = new GameSaveData(
                saveId,
                System.currentTimeMillis(),
                state.getCurrentPlayerIndex(),
                state.isClockwise(),
                state.getRoundNumber(),
                state.getPhase().name(),
                CardDTO.from(state.getTopCard()),
                state.getCurrentColor().name(),
                playerSnapshots
        );
 
        writeSave(saveId, saveData);
    }
    
    public Optional<GameSaveData> loadGame(String saveId) {
        validateSaveId(saveId);
 
        Path path = saveFilePath(saveId);
 
        if (!Files.exists(path)) return Optional.empty();
 
        return Optional.of(readSave(path));
    }
    
    public List<GameSaveData> listSaves() {
        if (!Files.exists(saveDirectory)) return Collections.emptyList();
 
        try (Stream<Path> files = Files.list(saveDirectory)) {
            return files
                    .filter(p -> p.getFileName().toString().startsWith(SAVE_PREFIX))
                    .filter(p -> p.getFileName().toString().endsWith(SAVE_EXTENSION))
                    .map(this::tryReadSave)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted((a, b) -> Long.compare(b.getSavedAt(), a.getSavedAt()))
                    .toList();
        }
        catch (IOException e) {
            throw new GameSaveException("Failed to list save files in: " + saveDirectory, e);
        }
    }
    
    public void deleteSave(String saveId) {
        validateSaveId(saveId);
 
        Path path = saveFilePath(saveId);
 
        if (!Files.exists(path)) return;
 
        try {
            Files.delete(path);
        }
        catch (IOException e) {
            throw new GameSaveException("Failed to delete save file: " + path, e);
        }
    }
    
    public boolean saveExists(String saveId) {
        validateSaveId(saveId);
        return Files.exists(saveFilePath(saveId));
    }
    
    public static String formatSaveTime(long savedAt) {
        return DISPLAY_FORMATTER.format(Instant.ofEpochMilli(savedAt));
    }
    
    /*
        PRIVATE HELPERS
    */
    private Path saveFilePath(String saveId) {
        return saveDirectory.resolve(SAVE_PREFIX + saveId + SAVE_EXTENSION);
    }
    
    private void writeSave(String saveId, GameSaveData saveData) {
        Path path = saveFilePath(saveId);
 
        try {
            Files.writeString(path, gson.toJson(saveData));
        }
        catch (IOException e) {
            throw new GameSaveException("Failed to write save file: " + path, e);
        }
    }
    
    private GameSaveData readSave(Path path) {
        try {
            String json = Files.readString(path);
 
            if (json == null || json.isBlank()) {
                throw new GameSaveException("Save file is empty: " + path, null);
            }
 
            GameSaveData data = gson.fromJson(json, GameSaveData.class);
 
            if (data == null) {
                throw new GameSaveException("Save file parsed to null — may be corrupt: " + path, null);
            }
 
            validateSaveData(data, path);
            return data;
        }
        catch (IOException e) {
            throw new GameSaveException("Failed to read save file: " + path, e);
        }
        catch (JsonSyntaxException e) {
            throw new GameSaveException("Save file contains invalid JSON: " + path, e);
        }
    }
    
    private Optional<GameSaveData> tryReadSave(Path path) {
        try {
            return Optional.of(readSave(path));
        }
        catch (GameSaveException e) {
            return Optional.empty();
        }
    }
    
    private void validateSaveData(GameSaveData data, Path path) {
        if (data.getSaveId() == null)
            throw new GameSaveException("Save file missing saveId: " + path, null);
        if (data.getPlayers() == null || data.getPlayers().isEmpty())
            throw new GameSaveException("Save file has no players: " + path, null);
        if (data.getTopCard() == null)
            throw new GameSaveException("Save file missing topCard: " + path, null);
        if (data.currentColor == null)
            throw new GameSaveException("Save file missing currentColor: " + path, null);
        if (data.phase == null)
            throw new GameSaveException("Save file missing phase: " + path, null);
 
        // Verify enum fields deserialize cleanly — catches typos in hand-edited files
        try {
            data.getPhase();
        }
        catch (IllegalArgumentException e) {
            throw new GameSaveException("Save file has unrecognised phase '" + data.phase + "': " + path, e);
        }
 
        try {
            data.getCurrentColor();
        }
        catch (IllegalArgumentException | IllegalStateException e) {
            throw new GameSaveException("Save file has invalid currentColor '" + data.currentColor + "': " + path, e);
        }
 
        for (PlayerSaveData player : data.getPlayers()) {
            if (player.getId() == null || player.getId().isBlank())
                throw new GameSaveException("Save file contains a player with no id: " + path, null);
            if (player.getHand() == null)
                throw new GameSaveException("Save file contains a null hand for player '" + player.getId() + "': " + path, null);
        }
    }
    
    private void ensureDirectoryExists() {
        if (Files.exists(saveDirectory)) return;
 
        try {
            Files.createDirectories(saveDirectory);
        }
        catch (IOException e) {
            throw new GameSaveException("Could not create save directory: " + saveDirectory, e);
        }
    }
    
    private void validateSaveId(String saveId) {
        if (saveId == null || saveId.isBlank())
            throw new IllegalArgumentException("Save id cannot be null or blank");
        if (saveId.chars().anyMatch(c -> "\\/:<>*?\"|".indexOf(c) >= 0))
            throw new IllegalArgumentException("Save id contains illegal characters: " + saveId);
        if (saveId.contains(".."))
            throw new IllegalArgumentException("Save id must not contain '..': " + saveId);
    }
    
    // Exception
    public static class GameSaveException extends RuntimeException {
        public GameSaveException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
