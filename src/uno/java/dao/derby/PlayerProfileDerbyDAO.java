package uno.java.dao.derby;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
 
import uno.java.dao.PlayerProfileDAO;
import uno.java.dto.PlayerProfileDTO;

public class PlayerProfileDerbyDAO implements PlayerProfileDAO {
    private final Connection conn;
    
    public PlayerProfileDerbyDAO(Connection conn) {
        if (conn == null) throw new IllegalArgumentException("conn cannot be null");
        this.conn = conn;
    }
    
    // implementation
    @Override
    public Optional<PlayerProfileDTO> findById(String id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT PLAYERID, PLAYERNAME, SCORE FROM PLAYER WHERE PLAYERID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("[PlayerProfileDerbyDAO] findById failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }
 
    @Override
    public Optional<PlayerProfileDTO> findByName(String name) {
        if (name == null) return Optional.empty();
        // Derby's UPPER() function lets us do a case-insensitive comparison without
        // requiring a case-insensitive collation to be configured at DB level.
        String sql = "SELECT PLAYERID, PLAYERNAME, SCORE FROM PLAYER "
                   + "WHERE UPPER(PLAYERNAME) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("[PlayerProfileDerbyDAO] findByName failed: " + e.getMessage(), e);
        }
        return Optional.empty();
    }
 
    @Override
    public List<PlayerProfileDTO> findAll() {
        String sql = "SELECT PLAYERID, PLAYERNAME, SCORE FROM PLAYER";
        List<PlayerProfileDTO> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("[PlayerProfileDerbyDAO] findAll failed: " + e.getMessage(), e);
        }
        return Collections.unmodifiableList(result);
    }
 
    @Override
    public void save(PlayerProfileDTO profile) {
        if (profile == null)
            throw new IllegalArgumentException("profile cannot be null");
        if (profile.id == null || profile.id.isBlank())
            throw new IllegalArgumentException("profile.id cannot be null or blank");
 
        try (PreparedStatement ps = conn.prepareStatement(DerbySchema.UPSERT_PLAYER)) {
            ps.setString(1, profile.id);
            ps.setString(2, profile.name != null ? profile.name : "");
            ps.setInt(3, profile.score);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("[PlayerProfileDerbyDAO] save failed: " + e.getMessage(), e);
        }
    }
    
    // private helper
    private static PlayerProfileDTO mapRow(ResultSet rs) throws SQLException {
        return new PlayerProfileDTO(
                rs.getString("PLAYERID"),
                rs.getString("PLAYERNAME"),
                rs.getInt("SCORE"));
    }
}
