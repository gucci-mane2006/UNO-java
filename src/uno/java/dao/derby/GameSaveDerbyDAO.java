package uno.java.dao.derby;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
 
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
 
import uno.java.dao.GameSaveDAO;
import uno.java.dto.GameSaveDTO;

public class GameSaveDerbyDAO implements GameSaveDAO {
    private static final int SAVE_SLOT_ID = 1;
 
    private final Connection conn;
    private final Gson       gson = new GsonBuilder().create();
 
    public GameSaveDerbyDAO(Connection conn) {
        if (conn == null) throw new IllegalArgumentException("conn cannot be null");
        this.conn = conn;
    }
    
    // implementation
    @Override
    public void save(GameSaveDTO save) {
        if (save == null) throw new IllegalArgumentException("save cannot be null");
        String json = gson.toJson(save);
        try {
            try (PreparedStatement ps = conn.prepareStatement(DerbySchema.UPDATE_GAME_SAVE)) {
                ps.setString(1, json);
                if (ps.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(DerbySchema.INSERT_GAME_SAVE)) {
                        ins.setString(1, json);
                        ins.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("[GameSaveDerbyDAO] save failed: " + e.getMessage(), e);
        }
    }
    @Override
    public Optional<GameSaveDTO> load() {
        String sql = "SELECT SAVE_JSON FROM GAME_SAVE WHERE SAVE_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SAVE_SLOT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("SAVE_JSON");
                    GameSaveDTO dto = gson.fromJson(json, GameSaveDTO.class);
                    return Optional.ofNullable(dto);
                }
            }
        } catch (SQLException e) {
            System.err.println("[GameSaveDerbyDAO] load failed: " + e.getMessage());
        }
        return Optional.empty();
    }
 
    @Override
    public void delete() {
        String sql = "DELETE FROM GAME_SAVE WHERE SAVE_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SAVE_SLOT_ID);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[GameSaveDerbyDAO] delete failed: " + e.getMessage());
        }
    }
 
    @Override
    public boolean exists() {
        String sql = "SELECT 1 FROM GAME_SAVE WHERE SAVE_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SAVE_SLOT_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[GameSaveDerbyDAO] exists check failed: " + e.getMessage());
            return false;
        }
    }
}
