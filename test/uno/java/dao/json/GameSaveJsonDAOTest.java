package uno.java.dao.json;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import uno.java.dao.AbstractGameSaveDAOTestBase;
import uno.java.dao.GameSaveDAO;

import static org.junit.Assert.*;

/**
 * Tests for {@link GameSaveJsonDAO}.
 *
 * Extends {@link AbstractGameSaveDAOTestBase} so the full DAO contract runs
 * against the JSON implementation automatically.
 *
 * JSON-specific tests cover: null path rejection, automatic directory
 * creation, file lifecycle on disk, cross-instance disk persistence,
 * and file content structure.
 *
 * <h3>Test isolation</h3>
 * Each test gets a dedicated temporary directory created in {@code @Before}
 * and deleted in {@code @After}. This avoids the ordering problem with
 * JUnit 4's {@code @Rule TemporaryFolder}, where the folder is not yet
 * initialised when the superclass {@code @Before} calls {@code createDAO()}.
 */
public class GameSaveJsonDAOTest extends AbstractGameSaveDAOTestBase {

    private Path tempDir;

    @Before
    @Override
    public void setUp() {
        try {
            tempDir = Files.createTempDirectory("uno_gamesave_test_");
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp directory for test", e);
        }
        super.setUp();
    }

    @After
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    // -------------------------------------------------------------------------
    // AbstractGameSaveDAOTestBase hook
    // -------------------------------------------------------------------------

    /**
     * Points the DAO at a "saves" sub-directory that does not yet exist —
     * the DAO must create it on first write.
     */
    @Override
    protected GameSaveDAO createDAO() {
        return new GameSaveJsonDAO(tempDir.resolve("saves"));
    }

    // =========================================================================
    // JSON-specific: constructor guard
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullSavesDir_throwsIllegalArgument() {
        new GameSaveJsonDAO(null);
    }

    // =========================================================================
    // JSON-specific: directory creation
    // =========================================================================

    /**
     * When the saves directory does not yet exist, save() must create it rather
     * than throwing an IOException.
     */
    @Test
    public void save_savesDirDoesNotExist_createsDirAndFile() {
        Path savesDir = tempDir.resolve("saves");
        assertFalse("Precondition: saves dir must not exist", Files.exists(savesDir));

        new GameSaveJsonDAO(savesDir).save(minimalSave());

        assertTrue(Files.isDirectory(savesDir));
        assertTrue(Files.exists(savesDir.resolve("game.json")));
    }

    // =========================================================================
    // JSON-specific: file lifecycle on disk
    // =========================================================================

    /**
     * After save(), game.json must be present on disk — confirming the DAO
     * writes through to the filesystem.
     */
    @Test
    public void save_snapshot_createsGameJsonOnDisk() {
        Path savesDir = tempDir.resolve("saves");
        new GameSaveJsonDAO(savesDir).save(minimalSave());

        assertTrue(Files.exists(savesDir.resolve("game.json")));
    }

    /**
     * After delete(), game.json must be removed from disk.
     */
    @Test
    public void delete_afterSave_removesGameJsonFromDisk() {
        Path savesDir = tempDir.resolve("saves");
        GameSaveDAO fileDao = new GameSaveJsonDAO(savesDir);
        fileDao.save(minimalSave());
        fileDao.delete();

        assertFalse(Files.exists(savesDir.resolve("game.json")));
    }

    /**
     * exists() must reflect file presence on disk. After the file is deleted
     * externally (bypassing the DAO), the same instance must return false.
     */
    @Test
    public void exists_fileDeletedExternally_returnsFalse() throws IOException {
        Path savesDir = tempDir.resolve("saves");
        GameSaveDAO fileDao = new GameSaveJsonDAO(savesDir);
        fileDao.save(minimalSave());

        Files.deleteIfExists(savesDir.resolve("game.json"));

        assertFalse(fileDao.exists());
    }

    /**
     * game.json must be non-empty after save().
     */
    @Test
    public void save_snapshot_fileContentIsNonEmpty() throws IOException {
        Path savesDir = tempDir.resolve("saves");
        new GameSaveJsonDAO(savesDir).save(minimalSave());

        assertTrue(Files.size(savesDir.resolve("game.json")) > 0);
    }

    // =========================================================================
    // JSON-specific: cross-instance persistence
    // =========================================================================

    /**
     * A snapshot saved by one instance must be loadable by a second instance
     * pointing at the same directory — confirms data reaches disk.
     */
    @Test
    public void save_thenConstructNewInstance_snapshotIsLoadedFromDisk() {
        Path savesDir = tempDir.resolve("saves");

        new GameSaveJsonDAO(savesDir).save(minimalSave()); // round 1

        GameSaveDAO second = new GameSaveJsonDAO(savesDir);
        assertTrue(second.exists());
        assertEquals(1, second.load().orElseThrow().roundNumber);
    }

    /**
     * An overwrite by one instance must be visible to a second instance —
     * the second instance must see the latest snapshot, not the first.
     */
    @Test
    public void save_overwriteThenNewInstance_latestSnapshotIsOnDisk() {
        Path savesDir = tempDir.resolve("saves");

        GameSaveDAO first = new GameSaveJsonDAO(savesDir);
        first.save(minimalSave());    // round 1
        first.save(alternateSave()); // round 3

        int loaded = new GameSaveJsonDAO(savesDir).load().orElseThrow().roundNumber;
        assertEquals(3, loaded);
    }

    /**
     * After one instance deletes the save, a second instance must see no save.
     */
    @Test
    public void delete_byOneInstance_secondInstanceSeesNoSave() {
        Path savesDir = tempDir.resolve("saves");

        GameSaveDAO first = new GameSaveJsonDAO(savesDir);
        first.save(minimalSave());
        first.delete();

        GameSaveDAO second = new GameSaveJsonDAO(savesDir);
        assertFalse(second.exists());
        assertFalse(second.load().isPresent());
    }

    // =========================================================================
    // JSON-specific: file content structure
    // =========================================================================

    /**
     * The file written by save() must begin with '{', confirming it is a JSON
     * object (the serialised GameSaveDTO). A regression here would break all
     * subsequent load() calls.
     */
    @Test
    public void save_snapshot_fileContentIsJsonObject() throws IOException {
        Path savesDir = tempDir.resolve("saves");
        new GameSaveJsonDAO(savesDir).save(minimalSave());

        String content = Files.readString(savesDir.resolve("game.json")).strip();
        assertTrue("game.json must start with '{'", content.startsWith("{"));
    }
}