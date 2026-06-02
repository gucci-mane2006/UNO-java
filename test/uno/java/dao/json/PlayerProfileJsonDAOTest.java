package uno.java.dao.json;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import uno.java.dao.AbstractPlayerProfileDAOTestBase;
import uno.java.dao.PlayerProfileDAO;
import uno.java.dto.PlayerProfileDTO;

import static org.junit.Assert.*;

/**
 * Tests for {@link PlayerProfileJsonDAO}.
 *
 * Extends {@link AbstractPlayerProfileDAOTestBase} so the full DAO contract runs
 * against the JSON implementation automatically.
 *
 * JSON-specific tests cover: null path rejection, missing-file startup,
 * corrupt-file recovery, and cross-instance disk persistence.
 *
 * <h3>Test isolation</h3>
 * Each test gets a dedicated temporary directory created in {@code @Before}
 * and deleted in {@code @After}. This avoids the ordering problem with
 * JUnit 4's {@code @Rule TemporaryFolder}, where the folder is not yet
 * initialised when the superclass {@code @Before} calls {@code createDAO()}.
 */
public class PlayerProfileJsonDAOTest extends AbstractPlayerProfileDAOTestBase {

    // Created fresh in @Before, deleted in @After.
    private Path tempDir;

    @Before
    @Override
    public void setUp() {
        try {
            tempDir = Files.createTempDirectory("uno_profiles_test_");
        } catch (IOException e) {
            throw new RuntimeException("Could not create temp directory for test", e);
        }
        // Superclass setUp() calls createDAO(), which now has tempDir ready.
        super.setUp();
    }

    @After
    public void tearDown() throws IOException {
        // Delete every file inside the temp directory, then the directory itself.
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    // -------------------------------------------------------------------------
    // AbstractPlayerProfileDAOTestBase hook
    // -------------------------------------------------------------------------

    @Override
    protected PlayerProfileDAO createDAO() {
        // tempDir is always set before this is called (setUp() assigns it first).
        return new PlayerProfileJsonDAO(tempDir.resolve("profiles.json"));
    }

    // =========================================================================
    // JSON-specific: constructor guard
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullPath_throwsIllegalArgument() {
        new PlayerProfileJsonDAO(null);
    }

    // =========================================================================
    // JSON-specific: missing file startup
    // =========================================================================

    /**
     * When the profiles file does not yet exist the DAO must start with an
     * empty store — no exception, no null return from findAll().
     */
    @Test
    public void constructor_missingFile_startsWithEmptyStore() {
        assertTrue(dao.findAll().isEmpty());
    }

    // =========================================================================
    // JSON-specific: corrupt file recovery
    // =========================================================================

    /**
     * A file containing invalid JSON must be silently discarded; the DAO must
     * start empty rather than propagating a parse exception to the caller.
     */
    @Test
    public void constructor_corruptJsonFile_recoversWithEmptyStore() throws IOException {
        Path profilesFile = tempDir.resolve("profiles.json");
        Files.writeString(profilesFile, "{ this is not valid json !!!");

        PlayerProfileDAO corruptDao = new PlayerProfileJsonDAO(profilesFile);

        assertTrue(corruptDao.findAll().isEmpty());
    }

    /**
     * After corrupt-file recovery the corrupt file must be deleted from disk
     * so that the next startup does not fail again.
     */
    @Test
    public void constructor_corruptJsonFile_deletesCorruptFileFromDisk() throws IOException {
        Path profilesFile = tempDir.resolve("profiles.json");
        Files.writeString(profilesFile, "not json");

        new PlayerProfileJsonDAO(profilesFile);

        assertFalse("Corrupt file must be deleted after recovery",
                Files.exists(profilesFile));
    }

    // =========================================================================
    // JSON-specific: persistence across instances
    // =========================================================================

    /**
     * Data written by one DAO instance must be readable by a second instance
     * constructed from the same file — confirms save() writes to disk and not
     * only to the in-memory mirror.
     */
    @Test
    public void save_thenConstructNewInstance_profileIsLoadedFromDisk() {
        Path profilesFile = tempDir.resolve("profiles.json");

        PlayerProfileDAO first = new PlayerProfileJsonDAO(profilesFile);
        first.save(new PlayerProfileDTO("human-1", "Alice", 7));

        PlayerProfileDAO second = new PlayerProfileJsonDAO(profilesFile);
        Optional<PlayerProfileDTO> result = second.findById("human-1");

        assertTrue(result.isPresent());
        assertEquals("Alice", result.get().name);
        assertEquals(7, result.get().score);
    }

    /**
     * An upsert performed by one instance must be visible to a fresh instance
     * reading the same file — the second save must overwrite the first on disk.
     */
    @Test
    public void save_upsert_updatedValuePersistedToDisk() {
        Path profilesFile = tempDir.resolve("profiles.json");

        PlayerProfileDAO first = new PlayerProfileJsonDAO(profilesFile);
        first.save(new PlayerProfileDTO("human-1", "Alice", 3));
        first.save(new PlayerProfileDTO("human-1", "Alice", 10));

        int score = new PlayerProfileJsonDAO(profilesFile).findById("human-1").orElseThrow().score;

        assertEquals(10, score);
    }

    /**
     * Multiple profiles written by one instance must all appear when a second
     * instance reads the same file.
     */
    @Test
    public void save_multipleProfiles_allPersistedToDisk() {
        Path profilesFile = tempDir.resolve("profiles.json");

        PlayerProfileDAO first = new PlayerProfileJsonDAO(profilesFile);
        first.save(new PlayerProfileDTO("human-1", "Alice", 5));
        first.save(new PlayerProfileDTO("human-2", "Bob",   9));

        PlayerProfileDAO second = new PlayerProfileJsonDAO(profilesFile);

        assertEquals(2, second.findAll().size());
        assertTrue(second.findByName("alice").isPresent());
        assertTrue(second.findByName("bob").isPresent());
    }

    // =========================================================================
    // JSON-specific: file content structure
    // =========================================================================

    /**
     * After save() the file must exist on disk — the DAO must write through
     * to the filesystem, not only update its in-memory mirror.
     */
    @Test
    public void save_profile_createsFileOnDisk() throws IOException {
        Path profilesFile = tempDir.resolve("profiles.json");
        new PlayerProfileJsonDAO(profilesFile)
                .save(new PlayerProfileDTO("human-1", "Alice", 0));

        assertTrue(Files.exists(profilesFile));
    }

    /**
     * The file written by save() must begin with '[', confirming it contains a
     * JSON array. A regression to a JSON object here would break load-on-startup.
     */
    @Test
    public void save_profile_fileContentIsJsonArray() throws IOException {
        Path profilesFile = tempDir.resolve("profiles.json");
        new PlayerProfileJsonDAO(profilesFile)
                .save(new PlayerProfileDTO("human-1", "Alice", 0));

        String content = Files.readString(profilesFile).strip();
        assertTrue("profiles.json must start with '['", content.startsWith("["));
    }
}