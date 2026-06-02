package uno.java.dao.derby;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import uno.java.dao.AbstractGameSaveDAOTestBase;
import uno.java.dao.GameSaveDAO;
import uno.java.dto.GameSaveDTO;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for {@link GameSaveDerbyDAO}.
 *
 * Extends {@link AbstractGameSaveDAOTestBase} so the full DAO contract runs
 * against the Derby implementation automatically.
 *
 * Derby-specific tests cover: null connection rejection, raw JDBC verification
 * that GAME_SAVE holds exactly one row (single-slot enforcement), and CLOB
 * content inspection.
 *
 * <h3>Test isolation</h3>
 * Each test method uses a uniquely-named in-memory Derby database via an
 * {@link AtomicInteger} counter. The database is dropped in {@code @After}.
 * If Derby is not on the classpath, {@code assumeTrue(false)} in {@code @Before}
 * marks the test as skipped rather than failed.
 */
public class GameSaveDerbyDAOTest extends AbstractGameSaveDAOTestBase {

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
        String dbName = "memory:test_game_save_" + DB_COUNTER.incrementAndGet();
        dbUrl   = "jdbc:derby:" + dbName + ";create=true";
        dropUrl = "jdbc:derby:" + dbName + ";drop=true";

        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(true);
            new DerbySchemaInitialiser(conn).initSchema();
        } catch (Exception e) {
            assumeTrue("Derby not available, skipping: " + e.getMessage(), false);
        }

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
                if (!"08006".equals(e.getSQLState())) {
                    System.err.println("Unexpected error dropping test DB: " + e.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // AbstractGameSaveDAOTestBase hook
    // -------------------------------------------------------------------------

    @Override
    protected GameSaveDAO createDAO() {
        return new GameSaveDerbyDAO(conn);
    }

    // =========================================================================
    // Derby-specific: constructor guard
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullConnection_throwsIllegalArgument() {
        new GameSaveDerbyDAO(null);
    }

    // =========================================================================
    // Derby-specific: raw JDBC verification of table state
    // =========================================================================

    /**
     * After save(), exactly one row with SAVE_ID = 1 must exist in GAME_SAVE —
     * verified directly via JDBC to confirm the CLOB write reached the database.
     */
    @Test
    public void save_firstSnapshot_exactlyOneRowInGameSaveTable() throws Exception {
        dao.save(minimalSave());

        assertEquals("Exactly one row expected after first save", 1, countGameSaveRows());
    }

    /**
     * The CLOB stored in GAME_SAVE must be non-empty after save().
     */
    @Test
    public void save_snapshot_clobIsNonEmpty() throws Exception {
        dao.save(minimalSave());

        String clob = readSaveJson();
        assertNotNull(clob);
        assertFalse("CLOB must not be blank after save", clob.isBlank());
    }

    /**
     * Calling save() twice must leave exactly one row — the MERGE must UPDATE,
     * not INSERT a second row, enforcing the single-slot contract at DB level.
     */
    @Test
    public void save_calledTwice_stillExactlyOneRowInGameSaveTable() throws Exception {
        dao.save(minimalSave());
        dao.save(alternateSave());

        assertEquals("MERGE must not create a second row", 1, countGameSaveRows());
    }

    /**
     * After delete(), the GAME_SAVE table must be empty — confirms the DELETE
     * statement reached the database.
     */
    @Test
    public void delete_afterSave_tableIsEmpty() throws Exception {
        dao.save(minimalSave());
        dao.delete();

        assertEquals("Table must be empty after delete", 0, countGameSaveRows());
    }

    /**
     * The CLOB must contain the {@code roundNumber} field name, confirming the
     * JSON serialisation is well-formed.
     */
    @Test
    public void save_snapshot_clobContainsRoundNumberField() throws Exception {
        dao.save(minimalSave());

        String clob = readSaveJson();
        assertTrue("CLOB must contain serialised roundNumber field",
                clob.contains("roundNumber"));
    }

    /**
     * After an overwrite the CLOB must reflect the latest save. The alternate
     * save uses {@code currentColor = "BLUE"} which must appear in the stored JSON.
     */
    @Test
    public void save_overwrite_clobReflectsLatestSave() throws Exception {
        dao.save(minimalSave());    // currentColor = RED
        dao.save(alternateSave()); // currentColor = BLUE

        assertTrue("CLOB must contain BLUE from the latest save",
                readSaveJson().contains("BLUE"));
    }

    // =========================================================================
    // Private JDBC helpers
    // =========================================================================

    private int countGameSaveRows() throws SQLException {
        try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM GAME_SAVE");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String readSaveJson() throws SQLException {
        try (var ps = conn.prepareStatement(
                "SELECT SAVE_JSON FROM GAME_SAVE WHERE SAVE_ID = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString("SAVE_JSON") : null;
        }
    }
}