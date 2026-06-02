package uno.java.dao.derby;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DerbySchemaInitialiser { // previously UNODatabase.initializeSchema()
    private static final String TABLE_EXISTS = "X0Y32"; // Derby SQLState for "table already exists"
    
    private final Connection conn;
    
    public DerbySchemaInitialiser(Connection conn) {
        if (conn == null) throw new IllegalArgumentException("conn cannot be null");
        this.conn = conn;
    }
    
    public void initSchema() throws SQLException {
        // Order matters: PLAYER_HAND and GAME_EVENT reference GAME and PLAYER.
        createIfAbsent(DerbySchema.CREATE_PLAYER);
        createIfAbsent(DerbySchema.CREATE_GAME_SAVE);
        createIfAbsent(DerbySchema.CREATE_CARD);
        createIfAbsent(DerbySchema.CREATE_GAME);
        createIfAbsent(DerbySchema.CREATE_PLAYER_HAND);
        createIfAbsent(DerbySchema.CREATE_GAME_EVENT);
    }
    
    private void createIfAbsent(String ddl) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(ddl);
        } catch (SQLException e) {
            if (!TABLE_EXISTS.equals(e.getSQLState())) throw e;
            // X0Y32 → table already exists → safe to ignore
        }
    }
}
