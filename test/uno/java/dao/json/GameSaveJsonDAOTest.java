package uno.java.dao.json;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import uno.java.dao.AbstractGameSaveDAOTest;
import uno.java.dao.AbstractGameSaveDAOTest;
import uno.java.dao.GameSaveDAO;
import uno.java.dao.GameSaveDAO;
import uno.java.dto.GameSaveDTO;

import static org.junit.Assert.*;

/**
 * Tests for {@link GameSaveJsonDAO}.
 *
 * <h3>Structure</h3>
 * This class extends {@link AbstractGameSaveDAOTest}, which defines the full
 * interface contract. Every test in that base class runs here automatically,
 * verifying that the JSON implementation satisfies the same contract as the
 * Derby one.
 *
 * The tests added here cover JSON-specific behaviour:
 * <ul>
 *   <li>The constructor must reject a null directory path.</li>
 *   <li>save() must create the saves directory if it does not exist.</li>
 *   <li>save() must write a non-empty {@code game.json} file to disk.</li>
 *   <li>delete() must remove the file from disk.</li>
 *   <li>A save written by one instance must be loadable by a second instance
 *       constructed from the same directory (cross-instance persistence).</li>
 *   <li>exists() must reflect file presence, not just in-memory state.</li>
 * </ul>
 *
 * <h3>Test isolation</h3>
 * JUnit's {@link TemporaryFolder} rule creates a fresh temporary directory for
 * each test and deletes it after the test completes.
 */
public class GameSaveJsonDAOTest extends AbstractGameSaveDAOTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // -------------------------------------------------------------------------
    // AbstractGameSaveDAOTest hook
    // -------------------------------------------------------------------------

    /**
     * Returns a DAO pointed at a saves sub-directory inside the temp folder.
     * The sub-directory does not exist yet — the DAO must create it on first write.
     */
    @Override
    protected GameSaveDAO createDAO() {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");
        return new GameSaveJsonDAO(savesDir);
    }

    // =========================================================================
    // JSON-specific: constructor guard
    // =========================================================================

    /**
     * Passing a null directory path to the constructor must throw immediately.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullSavesDir_throwsIllegalArgument() {
        new GameSaveJsonDAO(null);
    }

    // =========================================================================
    // JSON-specific: directory creation
    // =========================================================================

    /**
     * When the saves directory does not exist, save() must create it rather
     * than failing with an IOException. After the call the directory must exist.
     */
    @Test
    public void save_savesDirDoesNotExist_createsDirAndSavesFile() {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");
        assertFalse("Precondition: saves dir must not exist yet", Files.exists(savesDir));

        GameSaveDAO freshDao = new GameSaveJsonDAO(savesDir);
        freshDao.save(minimalSave());

        assertTrue("saves directory must be created by save()", Files.isDirectory(savesDir));
        assertTrue("game.json must exist inside the new directory",
                Files.exists(savesDir.resolve("game.json")));
    }

    // =========================================================================
    // JSON-specific: file lifecycle on disk
    // =========================================================================

    /**
     * After save(), game.json must exist on disk — the DAO must write to the
     * filesystem, not only update in-memory state.
     */
    @Test
    public void save_snapshot_createsGameJsonOnDisk() {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");
        GameSaveDAO fileDao = new GameSaveJsonDAO(savesDir);
        fileDao.save(minimalSave());

        assertTrue(Files.exists(savesDir.resolve("game.json")));
    }

    /**
     * After delete(), game.json must be removed from disk. This confirms that
     * delete() is a real file deletion, not just clearing in-memory state.
     */
    @Test
    public void delete_afterSave_removesGameJsonFromDisk() {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");
        GameSaveDAO fileDao = new GameSaveJsonDAO(savesDir);
        fileDao.save(minimalSave());
        fileDao.delete();

        assertFalse("game.json must be deleted from disk after delete()",
                Files.exists(savesDir.resolve("game.json")));
    }

    /**
     * exists() must reflect the presence of game.json on disk, not just
     * in-memory state. After the file is manually deleted (bypassing the DAO),
     * exists() on the same instance must return false.
     */
    @Test
    public void exists_fileDeletedExternally_returnsFalse() throws IOException {
        Path savesDir  = tempFolder.getRoot().toPath().resolve("saves");
        GameSaveDAO fileDao = new GameSaveJsonDAO(savesDir);
        fileDao.save(minimalSave());

        // Delete the file directly — simulates an external deletion or crash cleanup.
        Files.deleteIfExists(savesDir.resolve("game.json"));

        assertFalse("exists() must return false when game.json is absent from disk",
                fileDao.exists());
    }

    /**
     * The game.json file must be non-empty after save(), confirming the JSON
     * payload was written rather than producing a zero-byte file.
     */
    @Test
    public void save_snapshot_fileContentIsNonEmpty() throws IOException {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");
        GameSaveDAO fileDao = new GameSaveJsonDAO(savesDir);
        fileDao.save(minimalSave());

        long fileSize = Files.size(savesDir.resolve("game.json"));

        assertTrue("game.json must be non-empty after save", fileSize > 0);
    }

    // =========================================================================
    // JSON-specific: persistence across instances
    // =========================================================================

    /**
     * A snapshot saved by one DAO instance must be loadable by a second instance
     * constructed from the same saves directory. This confirms the data reaches
     * disk rather than remaining only in the first instance's memory.
     */
    @Test
    public void save_thenConstructNewInstance_snapshotIsLoadedFromDisk() {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");

        GameSaveDAO firstInstance = new GameSaveJsonDAO(savesDir);
        firstInstance.save(minimalSave()); // round 1

        GameSaveDAO secondInstance = new GameSaveJsonDAO(savesDir);

        assertTrue("Second instance must find the save written by the first",
                secondInstance.exists());
        assertEquals(1, secondInstance.load().orElseThrow().roundNumber);
    }

    /**
     * An overwrite performed by one instance must be visible to a second
     * instance. The second instance must load the latest snapshot, not the
     * original one.
     */
    @Test
    public void save_overwriteThenNewInstance_latestSnapshotIsOnDisk() {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");

        GameSaveDAO firstInstance = new GameSaveJsonDAO(savesDir);
        firstInstance.save(minimalSave());    // round 1
        firstInstance.save(alternateSave()); // round 3

        GameSaveDAO secondInstance = new GameSaveJsonDAO(savesDir);
        int loaded = secondInstance.load().orElseThrow().roundNumber;

        assertEquals("Second instance must read the most recent overwrite", 3, loaded);
    }

    /**
     * After delete() is called by one instance, a second instance constructed
     * from the same directory must see no save (exists returns false, load
     * returns empty).
     */
    @Test
    public void delete_byOneInstance_secondInstanceSeesNoSave() {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");

        GameSaveDAO firstInstance = new GameSaveJsonDAO(savesDir);
        firstInstance.save(minimalSave());
        firstInstance.delete();

        GameSaveDAO secondInstance = new GameSaveJsonDAO(savesDir);

        assertFalse("After delete, a new instance must report exists=false",
                secondInstance.exists());
        assertFalse("After delete, a new instance must return empty from load()",
                secondInstance.load().isPresent());
    }

    // =========================================================================
    // JSON-specific: file content structure
    // =========================================================================

    /**
     * The file written by save() must begin with '{', confirming it is a JSON
     * object (the serialised GameSaveDTO), not a JSON array or some other format.
     * A regression here would break load() for all subsequent runs.
     */
    @Test
    public void save_snapshot_fileContentIsJsonObject() throws IOException {
        Path savesDir = tempFolder.getRoot().toPath().resolve("saves");
        GameSaveDAO fileDao = new GameSaveJsonDAO(savesDir);
        fileDao.save(minimalSave());

        String content = Files.readString(savesDir.resolve("game.json")).strip();

        assertTrue("game.json must be a JSON object starting with '{'",
                content.startsWith("{"));
    }
}
