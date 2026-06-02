package uno.java.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
 
import uno.java.dao.PlayerProfileDAO;
import uno.java.dao.json.PlayerProfileJsonDAO;
import uno.java.dto.PlayerProfileDTO;

public class ProfileRepository implements PlayerProfileDAO {
    private final PlayerProfileDAO delegate;
    
    public ProfileRepository(Path profilesFile) {
        this.delegate = new PlayerProfileJsonDAO(profilesFile);
    }
    
    public ProfileRepository(PlayerProfileDAO delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate cannot be null");
        this.delegate = delegate;
    }
    
    // implementation
    @Override
    public Optional<PlayerProfileDTO> findById(String id) {
        return delegate.findById(id);
    }
 
    @Override
    public Optional<PlayerProfileDTO> findByName(String name) {
        return delegate.findByName(name);
    }
 
    @Override
    public List<PlayerProfileDTO> findAll() {
        return delegate.findAll();
    }
 
    @Override
    public void save(PlayerProfileDTO profile) {
        delegate.save(profile);
    }
    
    // legacy helpers
    @Deprecated
    public void setScore(String id, int absoluteScore) {
        findById(id).ifPresent(p -> {
            p.score = absoluteScore;
            save(p);
        });
    }
 
    /**
     * Alias retained for call-sites that used the old {@code saveProfile} name.
     *
     * @deprecated Use {@link #save(PlayerProfileDTO)} directly.
     */
    @Deprecated
    public void saveProfile(PlayerProfileDTO profile) {
        save(profile);
    }
 
    /**
     * Alias for {@link #findAll()} retained for compatibility.
     *
     * @deprecated Use {@link #findAll()} directly.
     */
    @Deprecated
    public List<PlayerProfileDTO> getAll() {
        return findAll();
    }
}
