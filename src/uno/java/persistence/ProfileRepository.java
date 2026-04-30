package uno.java.persistence;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

import uno.java.dto.*;

public class ProfileRepository {
    private final Path      profilesFile;
    private final Gson      gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<PlayerProfileDTO>    profiles;
    
    public ProfileRepository(Path profilesFile) {
        this.profilesFile = profilesFile;
        this.profiles     = load();
    }
    
    /*
        QUERIES
    */
    
    // Case insensitive search
    public Optional<PlayerProfileDTO> findByName(String name) {
        return profiles.stream()
                .filter(p -> p.name.equalsIgnoreCase(name))
                .findFirst();
    }
    
    public Optional<PlayerProfileDTO> findById(String id) {
        return profiles.stream()
                .filter(p -> p.id.equals(id))
                .findFirst();
    }
    
    public List<PlayerProfileDTO> getAll() {
        return Collections.unmodifiableList(profiles);
    }

    /*
        MUTATIONS
    */
    
    // Inserts/replaces the profile with the same id then persists
    public void saveProfile(PlayerProfileDTO profile) {
        profiles.removeIf(p -> p.id.equals(profile.id));
        profiles.add(profile);
        persist();
    }
    
    public void setScore(String id, int absoluteScore) {
        findById(id).ifPresent(p -> {
            p.score = absoluteScore;
            persist();
        });
    }
    
    /*
        PRIVATE HELPERS
    */
    
    private List<PlayerProfileDTO> load() {
        if (!Files.exists(profilesFile)) return new ArrayList<>();
        
        try (Reader reader = Files.newBufferedReader(profilesFile)) {
            Type listType = new TypeToken<List<PlayerProfileDTO>>() {}.getType();
            List <PlayerProfileDTO> result = gson.fromJson(reader, listType);
            return result != null ? result : new ArrayList<>();
        }
        catch (IOException e) {
            System.err.println("[ProfileRepository] Could not laod profiles: " + e.getMessage());
            return new ArrayList<>();
        }
        catch (JsonSyntaxException e) {
            System.err.println("[ProfileRepository] profiles.json has an unexpected structure"
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
        }
        catch (IOException e) {
            System.err.println("[ProfileRepository] Could not write profiles: " + e.getMessage());
        }
    }
}
