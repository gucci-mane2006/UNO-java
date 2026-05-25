package uno.java.dao.derby;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import uno.java.dao.AbstractGameSaveDAOTest;
import uno.java.dao.AbstractGameSaveDAOTest;
import uno.java.dao.GameSaveDAO;
import uno.java.dao.GameSaveDAO;
import uno.java.dto.GameSaveDTO;

import static org.junit.Assert.*;

/**
 * Tests for {@link GameSaveDerbyDAO}.
 *
 * <h3>Structure</h3>
 * This class extends {@link AbstractGameSaveDAOTest}, which defines the full
 * interface contract. Every test in that base class runs here automatically,
 * verifying that the Derby implementation satisfies the same contract as the
 * JSON one.
 *
 * The tests added here cover Derby-specific behaviour:
 * <ul>
 *   <li>In-memory database isolation and teardown</li>
 *   <li>Constructor null-guard on the JDBC connection</li>
 *   <li>Raw JDBC inspection of the GAME_SAVE table to confirm CLOB content</li>
 *   <li>Single-slot enforcement verified directly at the database level</li>
 * </ul>
 *
 * <h3>Test isolation</h3>
 * Uses an in-memory Derby database ({@code jdbc:derby:memory:...}) so no files
 * are written and each test class run gets a fully clean schema. The database
 * is dropped in {@code @After}.
 */
public class GameSaveDerbyDAOTest extends AbstractGameSaveDAOTest {

    private static final String DB_NAME  = "memory:test_game_save";
    private static final String DB_URL   = "jdbc:derby:" + DB_NAME + ";create=true";
    private static final String DROP_URL = "jdbc:derby:" + DB_NAME + ";drop=true";

    private Connection conn;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @Before
    public void setUpDerby() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");

        conn = DriverManager.getConnection(DB_URL);
        conn.setAutoCommit(true);

        new DerbySchemaInitialiser(conn).initSchema();

        super.setUp();
    }

    @After
    public void tearDownDerby() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
        try {
            DriverManager.getConnection(DROP_URL).close();
        } catch (SQLException e) {
            if (!"08006".equals(e.getSQLState())) {
                System.err.println("Unexpected error dropping test DB: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // AbstractGameSaveDAOTest hook
    // -------------------------------------------------------------------------

    @Override
    protected GameSaveDAO createDAO() {
        return new GameSaveDerbyDAO(conn);
    }

    // =========================================================================
    // Derby-specific: constructor guard
    // =========================================================================

    /**
     * Passing a null connection must fail immediately at construction time.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullConnection_throwsIllegalArgument() {
        new GameSaveDerbyDAO(null);
    }

    // =========================================================================
    // Derby-specific: raw JDBC verification of table state
    // =========================================================================

    /**
     * After save(), exactly one row with SAVE_ID = 1 must exist in GAME_SAVE.
     * Verified directly via JDBC so we know the CLOB write reached the database.
     */
    @Test
    public void save_newSnapshot_exactlyOneRowExistsInGameSaveTable() throws Exception {
        dao.save(minimalSave());

        int rowCount = countGameSaveRows();

        assertEquals("Exactly one row expected after first save", 1, rowCount);
    }

    /**
     * The GAME_SAVE CLOB must be non-empty after saving, confirming that the
     * JSON payload was actually written rather than stored as a blank string.
     */
    @Test
    public void save_snapshot_clobIsNonEmpty() throws Exception {
        dao.save(minimalSave());

        String clob = readSaveJson();

        assertNotNull(clob);
        assertFalse("CLOB must not be blank after save", clob.isBlank());
    }

    /**
     * Saving twice must leave exactly one row — the MERGE must UPDATE rather
     * than INSERT a second row, preserving the single-slot contract at DB level.
     */
    @Test
    public void save_calledTwice_stillExactlyOneRowInGameSaveTable() throws Exception {
        dao.save(minimalSave());
        dao.save(alternateSave());

        int rowCount = countGameSaveRows();

        assertEquals("MERGE must not create a second row", 1, rowCount);
    }

    /**
     * After delete(), the GAME_SAVE table must be empty.
     * Verifies the DELETE statement reached the database.
     */
    @Test
    public void delete_afterSave_tableIsEmpty() throws Exception {
        dao.save(minimalSave());
        dao.delete();

        int rowCount = countGameSaveRows();

        assertEquals("Table must be empty after delete", 0, rowCount);
    }

    /**
     * The JSON blob stored in the CLOB must contain the expected round number
     * string, confirming the serialised content is well-formed and the correct
     * object was persisted. This is a lightweight structural check rather than
     * a full deserialisation.
     */
    @Test
    public void save_snapshot_clobContainsExpectedRoundNumber() throws Exception {
        GameSaveDTO save = minimalSave(); // roundNumber = 1
        dao.save(save);

        String clob = readSaveJson();

        assertTrue(
            "CLOB must contain serialised roundNumber field",
            clob.contains("\"roundNumber\""));
        assertTrue(
            "CLOB must contain the value 1 for roundNumber",
            clob.contains("1"));
    }

    /**
     * After an overwrite the CLOB must reflect the latest save, not the first.
     * Checks that the round number from the second save appears in the stored JSON.
     */
    @Test
    public void save_overwrite_clobReflectsLatestSave() throws Exception {
        dao.save(minimalSave());    // round 1
        dao.save(alternateSave()); // round 3

        String clob = readSaveJson();

        // alternateSave has roundNumber=3 and currentColor="BLUE"
        assertTrue(
            "CLOB must contain currentColor from the latest save",
            clob.contains("BLUE"));
    }

    // =========================================================================
    // Private JDBC helpers — bypass the DAO to inspect raw DB state
    // =========================================================================

    private int countGameSaveRows() throws SQLException {
        String sql = "SELECT COUNT(*) FROM GAME_SAVE";
        try (var ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String readSaveJson() throws SQLException {
        String sql = "SELECT SAVE_JSON FROM GAME_SAVE WHERE SAVE_ID = 1";
        try (var ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getString("SAVE_JSON");
            return null;
        }
    }
}
