package uno.java.dao.json;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import uno.java.dao.AbstractPlayerProfileDAOTest;
import uno.java.dao.AbstractPlayerProfileDAOTest;
import uno.java.dao.PlayerProfileDAO;
import uno.java.dao.PlayerProfileDAO;
import uno.java.dto.PlayerProfileDTO;

import static org.junit.Assert.*;

/**
 * Tests for {@link PlayerProfileJsonDAO}.
 *
 * <h3>Structure</h3>
 * This class extends {@link AbstractPlayerProfileDAOTest}, which defines the
 * full interface contract. Every test in that base class runs here automatically,
 * verifying that the JSON implementation satisfies the same contract as the
 * Derby one.
 *
 * The tests added here cover JSON-specific behaviour:
 * <ul>
 *   <li>A missing file on first construction must start with an empty store
 *       rather than throwing.</li>
 *   <li>A corrupt JSON file must be handled gracefully: the file is deleted and
 *       the DAO starts with an empty store.</li>
 *   <li>Saved profiles must actually be written to disk as valid JSON, so a
 *       second DAO instance constructed from the same path can read them back
 *       (persistence across instances).</li>
 *   <li>The constructor must reject a null path.</li>
 * </ul>
 *
 * <h3>Test isolation</h3>
 * JUnit's {@link TemporaryFolder} rule creates a fresh temporary directory for
 * each test and deletes it (along with any files) after the test completes.
 * This means no test can observe file state left behind by another.
 */
public class PlayerProfileJsonDAOTest extends AbstractPlayerProfileDAOTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // -------------------------------------------------------------------------
    // AbstractPlayerProfileDAOTest hook
    // -------------------------------------------------------------------------

    /**
     * Called by the superclass {@code @Before} method before each test.
     * Returns a DAO pointed at a non-existent file inside the temp directory
     * — the file does not exist yet, so the DAO starts with an empty store.
     */
    @Override
    protected PlayerProfileDAO createDAO() {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");
        return new PlayerProfileJsonDAO(profilesFile);
    }

    // =========================================================================
    // JSON-specific: constructor guard
    // =========================================================================

    /**
     * Passing a null path to the constructor must throw immediately.
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullPath_throwsIllegalArgument() {
        new PlayerProfileJsonDAO(null);
    }

    // =========================================================================
    // JSON-specific: missing file startup
    // =========================================================================

    /**
     * When the profiles file does not yet exist the DAO must start with an empty
     * store rather than throwing or returning null from findAll().
     */
    @Test
    public void constructor_missingFile_startsWithEmptyStore() {
        // createDAO() already points at a non-existent file; this test confirms
        // the resulting DAO has zero profiles without any exception.
        assertTrue(dao.findAll().isEmpty());
    }

    // =========================================================================
    // JSON-specific: corrupt file recovery
    // =========================================================================

    /**
     * When the profiles file exists but contains invalid JSON, the DAO must
     * recover gracefully: the corrupt file is deleted and the store is treated
     * as empty. No exception must propagate to the caller.
     */
    @Test
    public void constructor_corruptJsonFile_recoversWithEmptyStore() throws IOException {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");
        Files.writeString(profilesFile, "{ this is not valid json !!!");

        PlayerProfileDAO corruptDao = new PlayerProfileJsonDAO(profilesFile);

        // Must not throw, and must start empty.
        assertTrue(corruptDao.findAll().isEmpty());
    }

    /**
     * After corrupt-file recovery the corrupt file must be deleted from disk,
     * so the next DAO instance starts fresh rather than failing again.
     */
    @Test
    public void constructor_corruptJsonFile_deletesCorruptFileFromDisk() throws IOException {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");
        Files.writeString(profilesFile, "not json");

        new PlayerProfileJsonDAO(profilesFile); // triggers recovery

        assertFalse("Corrupt file must be deleted after recovery",
                Files.exists(profilesFile));
    }

    // =========================================================================
    // JSON-specific: persistence across instances (data survives to disk)
    // =========================================================================

    /**
     * Data written by one DAO instance must be readable by a second instance
     * constructed from the same file path. This is the critical persistence
     * test: it confirms save() actually writes to disk, not just to memory.
     */
    @Test
    public void save_thenConstructNewInstance_profileIsLoadedFromDisk() {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");

        // First instance writes a profile.
        PlayerProfileDAO firstInstance = new PlayerProfileJsonDAO(profilesFile);
        firstInstance.save(new PlayerProfileDTO("human-1", "Alice", 7));

        // Second instance reads from the same file — must find Alice.
        PlayerProfileDAO secondInstance = new PlayerProfileJsonDAO(profilesFile);
        Optional<PlayerProfileDTO> result = secondInstance.findById("human-1");

        assertTrue("Profile saved by first instance must be found by second", result.isPresent());
        assertEquals("Alice", result.get().name);
        assertEquals(7,       result.get().score);
    }

    /**
     * An upsert (saving the same id twice) must persist the updated value to
     * disk. A fresh instance must see the updated score, not the original.
     */
    @Test
    public void save_upsert_updatedValuePersistedToDisk() {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");

        PlayerProfileDAO firstInstance = new PlayerProfileJsonDAO(profilesFile);
        firstInstance.save(new PlayerProfileDTO("human-1", "Alice", 3));
        firstInstance.save(new PlayerProfileDTO("human-1", "Alice", 10)); // overwrite

        PlayerProfileDAO secondInstance = new PlayerProfileJsonDAO(profilesFile);
        int score = secondInstance.findById("human-1").orElseThrow().score;

        assertEquals(10, score);
    }

    /**
     * Multiple profiles written by one instance must all be readable by a
     * subsequent instance, confirming findAll() draws from a complete file.
     */
    @Test
    public void save_multipleProfiles_allPersistedToDisk() {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");

        PlayerProfileDAO firstInstance = new PlayerProfileJsonDAO(profilesFile);
        firstInstance.save(new PlayerProfileDTO("human-1", "Alice", 5));
        firstInstance.save(new PlayerProfileDTO("human-2", "Bob",   9));

        PlayerProfileDAO secondInstance = new PlayerProfileJsonDAO(profilesFile);

        assertEquals(2, secondInstance.findAll().size());
        assertTrue(secondInstance.findByName("alice").isPresent()); // case-insensitive
        assertTrue(secondInstance.findByName("bob").isPresent());
    }

    // =========================================================================
    // JSON-specific: file content is valid JSON
    // =========================================================================

    /**
     * After saving a profile the file must exist on disk — confirming the DAO
     * actually writes rather than only updating its in-memory mirror.
     */
    @Test
    public void save_profile_createsFileOnDisk() throws IOException {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");
        PlayerProfileDAO fileDao = new PlayerProfileJsonDAO(profilesFile);

        fileDao.save(new PlayerProfileDTO("human-1", "Alice", 0));

        assertTrue("profiles.json must exist on disk after save", Files.exists(profilesFile));
    }

    /**
     * The file written by save() must start with '[', confirming it is a JSON
     * array (as opposed to a JSON object or some other format). This guards
     * against a serialisation regression that would break the load-on-startup path.
     */
    @Test
    public void save_profile_fileContentIsJsonArray() throws IOException {
        Path profilesFile = tempFolder.getRoot().toPath().resolve("profiles.json");
        PlayerProfileDAO fileDao = new PlayerProfileJsonDAO(profilesFile);

        fileDao.save(new PlayerProfileDTO("human-1", "Alice", 0));

        String content = Files.readString(profilesFile).strip();
        assertTrue("Profiles file must be a JSON array starting with '['",
                content.startsWith("["));
    }
}
