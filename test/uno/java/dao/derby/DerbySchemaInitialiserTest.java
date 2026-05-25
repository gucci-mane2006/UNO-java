package uno.java.dao.derby;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link DerbySchemaInitializer}.
 *
 * These tests verify that the schema initialiser:
 * <ol>
 *   <li>Creates every expected table on first run.</li>
 *   <li>Is idempotent — calling {@code initSchema()} a second time must not
 *       throw and must leave the schema unchanged.</li>
 *   <li>Rejects a null connection at construction time.</li>
 * </ol>
 *
 * All tests use an in-memory Derby database. The database is dropped in
 * {@code @After} so each test starts from a genuinely empty schema.
 */
public class DerbySchemaInitialiserTest {

    private static final String DB_NAME  = "memory:test_schema_init";
    private static final String DB_URL   = "jdbc:derby:" + DB_NAME + ";create=true";
    private static final String DROP_URL = "jdbc:derby:" + DB_NAME + ";drop=true";

    private Connection conn;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        conn = DriverManager.getConnection(DB_URL);
        conn.setAutoCommit(true);
    }

    @After
    public void tearDown() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        try {
            DriverManager.getConnection(DROP_URL).close();
        } catch (SQLException e) {
            if (!"08006".equals(e.getSQLState())) {
                System.err.println("Unexpected error dropping test schema DB: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // Constructor guard
    // =========================================================================

    /**
     * Passing a null connection must throw immediately rather than failing later
     * when a method is called on the null reference.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullConnection_throwsIllegalArgument() {
        new DerbySchemaInitialiser(null);
    }

    // =========================================================================
    // Table creation — first call
    // =========================================================================

    /**
     * After the first call to initSchema(), the PLAYER table must exist.
     */
    @Test
    public void initSchema_firstCall_createsPlayerTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue("PLAYER table must exist after initSchema()", tableExists("PLAYER"));
    }

    /**
     * After the first call to initSchema(), the GAME_SAVE table must exist.
     * This is the primary persistence table used by the active DAO layer.
     */
    @Test
    public void initSchema_firstCall_createsGameSaveTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue("GAME_SAVE table must exist after initSchema()", tableExists("GAME_SAVE"));
    }

    /**
     * After the first call to initSchema(), the CARD table must exist.
     */
    @Test
    public void initSchema_firstCall_createsCardTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue("CARD table must exist after initSchema()", tableExists("CARD"));
    }

    /**
     * After the first call to initSchema(), the GAME table must exist.
     */
    @Test
    public void initSchema_firstCall_createsGameTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue("GAME table must exist after initSchema()", tableExists("GAME"));
    }

    /**
     * After the first call to initSchema(), the PLAYER_HAND table must exist.
     */
    @Test
    public void initSchema_firstCall_createsPlayerHandTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue("PLAYER_HAND table must exist after initSchema()", tableExists("PLAYER_HAND"));
    }

    /**
     * After the first call to initSchema(), the GAME_EVENT table must exist.
     */
    @Test
    public void initSchema_firstCall_createsGameEventTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue("GAME_EVENT table must exist after initSchema()", tableExists("GAME_EVENT"));
    }

    /**
     * Consolidated check: all six expected tables must be present after one call.
     * Fails with a clear message listing any missing tables.
     */
    @Test
    public void initSchema_firstCall_allExpectedTablesExist() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        Set<String> expected = Set.of(
                "PLAYER", "GAME_SAVE", "CARD", "GAME", "PLAYER_HAND", "GAME_EVENT");
        Set<String> missing  = new HashSet<>();

        for (String table : expected) {
            if (!tableExists(table)) missing.add(table);
        }

        assertTrue("Missing tables after initSchema(): " + missing, missing.isEmpty());
    }

    // =========================================================================
    // Idempotency — second call
    // =========================================================================

    /**
     * Calling initSchema() a second time must not throw. Derby would throw
     * SQLState X0Y32 for each CREATE TABLE if the initialiser did not suppress
     * that specific error. This test confirms the suppression is working.
     */
    @Test
    public void initSchema_calledTwice_doesNotThrow() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();
        new DerbySchemaInitialiser(conn).initSchema(); // must complete without exception
    }

    /**
     * After calling initSchema() twice, the set of tables must be identical to
     * the set after one call — no duplicate tables, no additional tables, none
     * removed.
     */
    @Test
    public void initSchema_calledTwice_tableSetIsUnchanged() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();
        Set<String> afterFirst = getAllTableNames();

        new DerbySchemaInitialiser(conn).initSchema();
        Set<String> afterSecond = getAllTableNames();

        assertEquals("Table set must be identical after second initSchema() call",
                afterFirst, afterSecond);
    }

    // =========================================================================
    // Private JDBC helpers
    // =========================================================================

    /**
     * Queries {@link DatabaseMetaData} to check whether a table with the given
     * name (upper-cased, as Derby stores it) exists in the current schema.
     */
    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * Returns the set of all user-created table names in the current Derby
     * schema, upper-cased. Used to compare table sets across two initSchema() calls.
     */
    private Set<String> getAllTableNames() throws SQLException {
        Set<String> names = new HashSet<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, "APP", null, new String[]{"TABLE"})) {
            while (rs.next()) {
                names.add(rs.getString("TABLE_NAME").toUpperCase());
            }
        }
        return names;
    }
}
