package uno.java.dao.derby;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DerbyConnectionManager { // previously "DBManager"
    private static final String DB_NAME = "unodb";
    // Embedded Derby URL: create=true to create DB if missing
    private static final String URL     = "jdbc:derby:" + DB_NAME + ";create=true";
    
    private final Connection conn;
    
    public DerbyConnectionManager() {
        this.conn = open();
    }
    
    public Connection getConncetion() {
        return conn;
    }
    
    public void close() {
        if (conn == null) return;
        try {
            conn.close();
        } 
        catch (SQLException e) {
            System.err.println("[DerbyConnectionManager] Error closing connection: " + e.getMessage());
        }
        // Derby embedded shutdown: always throws SQLException on success (SQLState 08006 / XJ015).
        try {
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException e) {
            // SQLState XJ015 = successful engine-level shutdown (expected).
            // SQLState 08006 = successful database-level shutdown (also acceptable).
            if (!"XJ015".equals(e.getSQLState()) && !"08006".equals(e.getSQLState())) {
                System.err.println("[DerbyConnectionManager] Unexpected shutdown error: "
                        + e.getMessage());
            }
        }
    }
    
    private static Connection open() {
        try {
            Connection c = DriverManager.getConnection(URL);
            c.setAutoCommit(true);
            return c;
        } 
        catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to open Derby connection. Ensure derby.jar is on the classpath. Error: " + e.getMessage(), e);
        }
    }
}
