package uno.java.dao.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
 
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
 
import uno.java.dao.PlayerProfileDAO;
import uno.java.dto.PlayerProfileDTO;

public class PlayerProfileJsonDAO implements PlayerProfileDAO {
    private final Path profilesFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
 
    // In-memory mirror of the JSON file mutated by save() and flushed to disk
    private final List<PlayerProfileDTO> profiles;
 
    public PlayerProfileJsonDAO(Path profilesFile) {
        if (profilesFile == null) throw new IllegalArgumentException("profilesFile cannot be null");
        this.profilesFile = profilesFile;
        this.profiles     = load();
    }
 
    // implementation
    @Override
    public Optional<PlayerProfileDTO> findById(String id) {
        if (id == null) return Optional.empty();
        return profiles.stream()
                .filter(p -> id.equals(p.id))
                .findFirst();
    }
 
    @Override
    public Optional<PlayerProfileDTO> findByName(String name) {
        if (name == null) return Optional.empty();
        return profiles.stream()
                .filter(p -> p.name != null && p.name.equalsIgnoreCase(name))
                .findFirst();
    }
 
    @Override
    public List<PlayerProfileDTO> findAll() {
        return Collections.unmodifiableList(profiles);
    }
 
    /**
     * Upserts the profile by {@code id}: removes any existing entry with the
     * same id, appends the new record, then flushes to disk.
     */
    @Override
    public void save(PlayerProfileDTO profile) {
        if (profile == null)
            throw new IllegalArgumentException("profile cannot be null");
        if (profile.id == null || profile.id.isBlank())
            throw new IllegalArgumentException("profile.id cannot be null or blank");
 
        profiles.removeIf(p -> profile.id.equals(p.id));
        profiles.add(profile);
        persist();
    }
 
    // Private helpers
    private List<PlayerProfileDTO> load() {
        if (!Files.exists(profilesFile)) return new ArrayList<>();
 
        try (Reader reader = Files.newBufferedReader(profilesFile)) {
            Type listType = new TypeToken<List<PlayerProfileDTO>>() {}.getType();
            List<PlayerProfileDTO> result = gson.fromJson(reader, listType);
            return result != null ? result : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[PlayerProfileJsonDAO] Could not load profiles: " + e.getMessage());
            return new ArrayList<>();
        } catch (JsonSyntaxException e) {
            System.err.println("[PlayerProfileJsonDAO] profiles.json has an unexpected structure"
                    + " and will be reset. (" + e.getMessage() + ")");
            try { Files.deleteIfExists(profilesFile); } catch (IOException ignored) {}
            return new ArrayList<>();
        }
    }
 
    private void persist() {
        try {
            if (profilesFile.getParent() != null)
                Files.createDirectories(profilesFile.getParent());
            Files.writeString(profilesFile, gson.toJson(profiles));
        } catch (IOException e) {
            System.err.println("[PlayerProfileJsonDAO] Could not write profiles: " + e.getMessage());
        }
    }
}
