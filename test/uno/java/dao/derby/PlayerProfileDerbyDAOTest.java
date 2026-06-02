package uno.java.dao.derby;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import uno.java.dao.AbstractPlayerProfileDAOTestBase;
import uno.java.dao.PlayerProfileDAO;
import uno.java.dto.PlayerProfileDTO;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for {@link PlayerProfileDerbyDAO}.
 *
 * Extends {@link AbstractPlayerProfileDAOTestBase} so the full DAO contract runs
 * against the Derby implementation automatically.
 *
 * Derby-specific tests cover: null connection rejection, schema idempotency,
 * and raw JDBC verification that MERGE produces exactly one row per id.
 *
 * <h3>Test isolation</h3>
 * Each test method gets its own uniquely-named in-memory Derby database
 * (e.g., {@code jdbc:derby:memory:test_player_profiles_1;create=true}).
 * Derby keeps a dropped in-memory database's name reserved for the rest of
 * the JVM run, so an {@link AtomicInteger} counter ensures every test uses
 * a name that has never been used before. The database is dropped in
 * {@code @After} with {@code drop=true}.
 *
 * <h3>Derby availability</h3>
 * If the Derby JAR is not on the classpath, {@code DriverManager.getConnection}
 * throws in {@code @Before}. The test calls {@code assumeTrue(false)} at that
 * point, which causes JUnit 4 to mark the test as <em>skipped</em> rather
 * than <em>failed</em>, and no subsequent code in that test method runs.
 */
public class PlayerProfileDerbyDAOTest extends AbstractPlayerProfileDAOTestBase {

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private String     dbUrl;
    private String     dropUrl;
    private Connection conn;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @Before
    @Override
    public void setUp() {
        String dbName = "memory:test_player_profiles_" + DB_COUNTER.incrementAndGet();
        dbUrl   = "jdbc:derby:" + dbName + ";create=true";
        dropUrl = "jdbc:derby:" + dbName + ";drop=true";

        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(true);
            new DerbySchemaInitialiser(conn).initSchema();
        } catch (Exception e) {
            // Derby JAR not on classpath or failed to initialise — skip this test.
            assumeTrue("Derby not available, skipping: " + e.getMessage(), false);
        }

        // conn is now non-null; superclass setUp() calls createDAO().
        super.setUp();
    }

    @After
    public void tearDownDerby() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        if (dropUrl != null) {
            try {
                DriverManager.getConnection(dropUrl).close();
            } catch (SQLException e) {
                // SQLState 08006 = successful drop (Derby always throws on drop).
                if (!"08006".equals(e.getSQLState())) {
                    System.err.println("Unexpected error dropping test DB: " + e.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // AbstractPlayerProfileDAOTestBase hook
    // -------------------------------------------------------------------------

    @Override
    protected PlayerProfileDAO createDAO() {
        return new PlayerProfileDerbyDAO(conn);
    }

    // =========================================================================
    // Derby-specific: constructor guard
    // =========================================================================

    /**
     * Passing a null connection to the constructor must throw immediately rather
     * than producing a DAO that fails at query time.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullConnection_throwsIllegalArgument() {
        new PlayerProfileDerbyDAO(null);
    }

    // =========================================================================
    // Derby-specific: schema idempotency
    // =========================================================================

    /**
     * Calling {@link DerbySchemaInitializer#initSchema()} twice on the same
     * connection must not throw. Every CREATE TABLE hits SQLState X0Y32 on the
     * second call; the initialiser must suppress that state silently.
     */
    @Test
    public void schemaInitializer_calledTwice_doesNotThrow() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema();
    }

    // =========================================================================
    // Derby-specific: raw JDBC verification of UPSERT behaviour
    // =========================================================================

    /**
     * After save() with a new id, exactly one row must exist in the PLAYER
     * table — verified directly via JDBC to confirm the write reached the DB.
     */
    @Test
    public void save_newProfile_exactlyOneRowExistsInPlayerTable() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 5));

        assertEquals("Exactly one row expected after insert", 1, countPlayerRows("human-1"));
    }

    /**
     * Saving the same id twice must leave exactly one row — the MERGE must
     * UPDATE rather than INSERT a duplicate row.
     */
    @Test
    public void save_sameIdTwice_stillExactlyOneRowInPlayerTable() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-1", "Alice", 10));

        assertEquals("MERGE must not create a second row", 1, countPlayerRows("human-1"));
    }

    /**
     * After an upsert the SCORE column must hold the value from the second
     * save — confirmed via a direct JDBC query that bypasses any DAO caching.
     */
    @Test
    public void save_upsert_updatedScoreIsStoredInDatabase() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-1", "Alice", 10));

        assertEquals("MERGE must update the SCORE column", 10, readScoreFromDb("human-1"));
    }

    /**
     * Each save with a distinct id must produce its own independent row,
     * confirming the INSERT branch of the MERGE fires correctly for new ids.
     */
    @Test
    public void save_threeDistinctIds_threeRowsInDatabase() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 1));
        dao.save(new PlayerProfileDTO("human-2", "Bob",   2));
        dao.save(new PlayerProfileDTO("ai-1",    "CPU",   0));

        assertEquals(3, dao.findAll().size());
    }

    // =========================================================================
    // Private JDBC helpers
    // =========================================================================

    private int countPlayerRows(String playerId) throws SQLException {
        try (var ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM PLAYER WHERE PLAYERID = ?")) {
            ps.setString(1, playerId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int readScoreFromDb(String playerId) throws SQLException {
        try (var ps = conn.prepareStatement(
                "SELECT SCORE FROM PLAYER WHERE PLAYERID = ?")) {
            ps.setString(1, playerId);
            try (var rs = ps.executeQuery()) {
                assertTrue("No row found for id: " + playerId, rs.next());
                return rs.getInt("SCORE");
            }
        }
    }
}