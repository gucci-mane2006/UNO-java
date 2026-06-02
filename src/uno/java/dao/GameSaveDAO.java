package uno.java.dao;

import java.util.Optional;
 
import uno.java.dto.GameSaveDTO;

public interface GameSaveDAO {
    void save(GameSaveDTO save);
    Optional<GameSaveDTO> load();
    void delete();
    boolean exists();
}
