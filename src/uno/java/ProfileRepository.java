package uno.java;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
 
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class ProfileRepository {
    private final Path filePath;
    private final Gson gson;
 
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerProfile>>(){}.getType();
    
    // Constructor
    public ProfileRepository(Path filePath) {
        if (filePath == null) throw new IllegalArgumentException("File path cannot be null");
 
        this.filePath = filePath;
        this.gson     = new GsonBuilder().setPrettyPrinting().create();
    }
 
    /*
        PUBLIC API
    */
    public Optional<PlayerProfile> loadProfile(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be null or blank");
 
        Map<String, PlayerProfile> profiles = readAll();
        return Optional.ofNullable(profiles.get(id));
    }
    
    public void saveProfile(PlayerProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Profile cannot be null");
 
        Map<String, PlayerProfile> profiles = readAll();
        profiles.put(profile.getId(), profile);
        writeAll(profiles);
    }
    
    public List<PlayerProfile> listProfiles() {
        return readAll().values()
                .stream()
                .sorted(Comparator.comparing(PlayerProfile::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
    
    public void deleteProfile(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be null or blank");
 
        Map<String, PlayerProfile> profiles = readAll();
 
        if (!profiles.containsKey(id)) return;
 
        profiles.remove(id);
        writeAll(profiles);
    }
    
    public boolean exists(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be null or blank");
        return readAll().containsKey(id);
    }
    
    public void renameProfile(String id, String newName) {
        if (id == null || id.isBlank())         throw new IllegalArgumentException("Id cannot be null or blank");
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("New name cannot be null or blank");
 
        Map<String, PlayerProfile> profiles = readAll();
        PlayerProfile profile = profiles.get(id);
 
        if (profile == null) throw new NoSuchElementException("No profile found with id: " + id);
 
        profile.setName(newName);
        writeAll(profiles);
    }

    public void addScore(String id, int points) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Id cannot be null or blank");
        if (points < 0)                 throw new IllegalArgumentException("Points cannot be negative");
 
        Map<String, PlayerProfile> profiles = readAll();
        PlayerProfile profile = profiles.get(id);
 
        if (profile == null) throw new NoSuchElementException("No profile found with id: " + id);
 
        profile.addScore(points);
        writeAll(profiles);
    }
 
    /*
        PRIVATE HELPERS
    */
    
    private Map<String, PlayerProfile> readAll() {
        if (!Files.exists(filePath)) return new LinkedHashMap<>();
 
        try {
            String json = Files.readString(filePath);
 
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
 
            Map<String, PlayerProfile> result = gson.fromJson(json, MAP_TYPE);
            return result != null ? new LinkedHashMap<>(result) : new LinkedHashMap<>();
        }
        catch (IOException e) {
            throw new ProfileRepositoryException("Failed to read profiles from: " + filePath, e);
        }
    }
    
    private void writeAll(Map<String, PlayerProfile> profiles) {
        try {
            String json = gson.toJson(profiles);
            Files.writeString(filePath, json);
        }
        catch (IOException e) {
            throw new ProfileRepositoryException("Failed to write profiles to: " + filePath, e);
        }
    }
    
    // Exception
    public static class ProfileRepositoryException extends RuntimeException {
        public ProfileRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
