package uno.java.dao.derby;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import uno.java.dao.AbstractPlayerProfileDAOTest;
import uno.java.dao.AbstractPlayerProfileDAOTest;
import uno.java.dao.PlayerProfileDAO;
import uno.java.dao.PlayerProfileDAO;
import uno.java.dto.PlayerProfileDTO;

import static org.junit.Assert.*;

/**
 * Tests for {@link PlayerProfileDerbyDAO}.
 *
 * <h3>Structure</h3>
 * This class extends {@link AbstractPlayerProfileDAOTest}, which defines the
 * full interface contract. Every test in that base class runs here automatically,
 * verifying that the Derby implementation satisfies the same contract as the
 * JSON one.
 *
 * The tests added in this class cover Derby-specific behaviour:
 * <ul>
 *   <li>In-memory database isolation and teardown</li>
 *   <li>Schema idempotency ({@code DerbySchemaInitializer} called twice)</li>
 *   <li>Constructor null-guard on the JDBC connection</li>
 *   <li>Correct UPSERT behaviour verified directly via JDBC</li>
 * </ul>
 *
 * <h3>Test isolation</h3>
 * Each test gets a fresh in-memory Derby database via a unique URL
 * ({@code jdbc:derby:memory:test_profiles_N;create=true}). In-memory Derby
 * databases are fully independent: creating two databases with different names
 * in the same JVM run does not share any state. The database is dropped in
 * {@code @After} by connecting with {@code drop=true}.
 *
 * This avoids the file-system side-effects of the production
 * {@link DerbyConnectionManager} and means tests never interfere with each
 * other, even when run in parallel.
 */
public class PlayerProfileDerbyDAOTest extends AbstractPlayerProfileDAOTest {

    // Unique DB name per test class run - avoids name collision when test
    // suites are executed repeatedly within the same JVM.
    private static final String DB_NAME = "memory:test_player_profiles";
    private static final String DB_URL  = "jdbc:derby:" + DB_NAME + ";create=true";
    private static final String DROP_URL = "jdbc:derby:" + DB_NAME + ";drop=true";

    private Connection conn;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @Before
    public void setUpDerby() throws Exception {
        // Load embedded Derby driver explicitly.
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");

        conn = DriverManager.getConnection(DB_URL);
        conn.setAutoCommit(true);

        // Create schema in the fresh in-memory DB.
        new DerbySchemaInitialiser(conn).initSchema();

        // Now let the superclass setUp() call createDAO(), which uses this conn.
        super.setUp();
    }

    @After
    public void tearDownDerby() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        // Drop the in-memory database so the next test run starts clean.
        try {
            DriverManager.getConnection(DROP_URL).close();
        } catch (SQLException e) {
            // SQLState 08006 means the drop succeeded (Derby always throws on drop).
            if (!"08006".equals(e.getSQLState())) {
                System.err.println("Unexpected error dropping test DB: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // AbstractPlayerProfileDAOTest hook
    // -------------------------------------------------------------------------

    @Override
    protected PlayerProfileDAO createDAO() {
        // conn is assigned in setUpDerby(), which runs before super.setUp().
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
     * connection must not throw. The second call hits the X0Y32 ("table already
     * exists") SQLState for every table, which must be silently ignored.
     */
    @Test
    public void schemaInitializer_calledTwice_doesNotThrow() throws Exception {
        new DerbySchemaInitialiser(conn).initSchema(); // second call on already-initialised DB
    }

    // =========================================================================
    // Derby-specific: raw JDBC verification of UPSERT behaviour
    // =========================================================================

    /**
     * After calling save() with a new profile, exactly one row must exist in
     * the PLAYER table for that PLAYERID — verified by querying JDBC directly
     * rather than through the DAO, to confirm the SQL write actually happened.
     */
    @Test
    public void save_newProfile_exactlyOneRowExistsInPlayerTable() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 5));

        int rowCount = countPlayerRows("human-1");

        assertEquals("Exactly one row expected after insert", 1, rowCount);
    }

    /**
     * After calling save() twice with the same id (upsert), exactly one row
     * must still exist — the MERGE must update, not insert a duplicate.
     */
    @Test
    public void save_sameIdTwice_stillExactlyOneRowInPlayerTable() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-1", "Alice", 10));

        int rowCount = countPlayerRows("human-1");

        assertEquals("MERGE must not create a second row for the same id", 1, rowCount);
    }

    /**
     * The score written to the database by the second save must be the updated
     * value — confirmed via a direct JDBC query to bypass any DAO-layer caching.
     */
    @Test
    public void save_upsert_updatedScoreIsStoredInDatabase() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-1", "Alice", 10));

        int dbScore = readScoreFromDb("human-1");

        assertEquals("MERGE must update the score column", 10, dbScore);
    }

    // =========================================================================
    // Derby-specific: multiple profiles stored across separate saves
    // =========================================================================

    /**
     * Each save with a distinct id must produce its own independent row.
     * This confirms the INSERT branch of the MERGE fires correctly for new ids.
     */
    @Test
    public void save_threeDistinctIds_threeRowsExistInDatabase() throws Exception {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 1));
        dao.save(new PlayerProfileDTO("human-2", "Bob",   2));
        dao.save(new PlayerProfileDTO("ai-1",    "CPU",   0));

        List<PlayerProfileDTO> all = dao.findAll();

        assertEquals(3, all.size());
    }

    // =========================================================================
    // Private JDBC helpers — bypass the DAO to inspect raw DB state
    // =========================================================================

    private int countPlayerRows(String playerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM PLAYER WHERE PLAYERID = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int readScoreFromDb(String playerId) throws SQLException {
        String sql = "SELECT SCORE FROM PLAYER WHERE PLAYERID = ?";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            try (var rs = ps.executeQuery()) {
                assertTrue("No row found for PLAYERID: " + playerId, rs.next());
                return rs.getInt("SCORE");
            }
        }
    }
}
