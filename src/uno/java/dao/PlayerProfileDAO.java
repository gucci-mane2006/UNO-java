package uno.java.dao;

import java.util.List;
import java.util.Optional;

import uno.java.dto.PlayerProfileDTO;

public interface PlayerProfileDAO {
    Optional<PlayerProfileDTO> findById(String id);
    Optional<PlayerProfileDTO> findByName(String name);
    List<PlayerProfileDTO> findAll();
    void save(PlayerProfileDTO profile);
}
