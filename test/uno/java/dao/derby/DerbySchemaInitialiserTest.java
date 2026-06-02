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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for {@link DerbySchemaInitializer}.
 *
 * Verifies that the initialiser creates every expected table on first run,
 * is fully idempotent on subsequent runs, and rejects a null connection.
 *
 * Each test uses a uniquely-named in-memory Derby database. If Derby is not
 * on the classpath the test is skipped via {@code assumeTrue(false)}.
 */
public class DerbySchemaInitialiserTest {

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private String     dbUrl;
    private String     dropUrl;
    private Connection conn;

    @Before
    public void setUp() {
        String dbName = "memory:test_schema_init_" + DB_COUNTER.incrementAndGet();
        dbUrl   = "jdbc:derby:" + dbName + ";create=true";
        dropUrl = "jdbc:derby:" + dbName + ";drop=true";

        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(true);
        } catch (Exception e) {
            assumeTrue("Derby not available, skipping: " + e.getMessage(), false);
        }
    }

    @After
    public void tearDown() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        if (dropUrl != null) {
            try {
                DriverManager.getConnection(dropUrl).close();
            } catch (SQLException e) {
                if (!"08006".equals(e.getSQLState())) {
                    System.err.println("Unexpected error dropping test DB: " + e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // Constructor guard
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullConnection_throwsIllegalArgument() {
        new DerbySchemaInitialiser(null);
    }

    // =========================================================================
    // Table creation — first call
    // =========================================================================

    @Test
    public void initSchema_firstCall_createsPlayerTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue(tableExists("PLAYER"));
    }

    @Test
    public void initSchema_firstCall_createsGameSaveTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue(tableExists("GAME_SAVE"));
    }

    @Test
    public void initSchema_firstCall_createsCardTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue(tableExists("CARD"));
    }

    @Test
    public void initSchema_firstCall_createsGameTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue(tableExists("GAME"));
    }

    @Test
    public void initSchema_firstCall_createsPlayerHandTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue(tableExists("PLAYER_HAND"));
    }

    @Test
    public void initSchema_firstCall_createsGameEventTable() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        assertTrue(tableExists("GAME_EVENT"));
    }

    /**
     * Consolidated check: all six expected tables must exist after one call.
     */
    @Test
    public void initSchema_firstCall_allExpectedTablesExist() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();

        Set<String> expected = Set.of(
                "PLAYER", "GAME_SAVE", "CARD", "GAME", "PLAYER_HAND", "GAME_EVENT");
        Set<String> missing = new HashSet<>();
        for (String table : expected) {
            if (!tableExists(table)) missing.add(table);
        }

        assertTrue("Missing tables after initSchema(): " + missing, missing.isEmpty());
    }

    // =========================================================================
    // Idempotency — second call
    // =========================================================================

    /**
     * Calling initSchema() twice must not throw. Every CREATE TABLE hits
     * SQLState X0Y32 on the second call; the initialiser must suppress it.
     */
    @Test
    public void initSchema_calledTwice_doesNotThrow() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();
        new DerbySchemaInitialiser(conn).initSchema();
    }

    /**
     * After two calls the set of tables must be identical to the set after one
     * call — no duplicates created, none removed.
     */
    @Test
    public void initSchema_calledTwice_tableSetIsUnchanged() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();
        Set<String> afterFirst = getAllTableNames();

        new DerbySchemaInitialiser(conn).initSchema();
        Set<String> afterSecond = getAllTableNames();

        assertEquals(afterFirst, afterSecond);
    }

    // =========================================================================
    // Private JDBC helpers
    // =========================================================================

    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(
                null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private Set<String> getAllTableNames() throws SQLException {
        Set<String> names = new HashSet<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, "APP", null, new String[]{"TABLE"})) {
            while (rs.next()) names.add(rs.getString("TABLE_NAME").toUpperCase());
        }
        return names;
    }
}