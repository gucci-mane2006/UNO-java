package uno.java.dao;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import uno.java.dto.PlayerProfileDTO;

import static org.junit.Assert.*;

/**
 * Contract tests for {@link PlayerProfileDAO}.
 *
 * Every method in the interface has at least one test here. Both the JSON and
 * Derby implementations extend this class and run the full suite automatically,
 * proving the two backends are interchangeable from the caller's perspective.
 *
 * Subclasses supply a clean, empty DAO instance via {@link #createDAO()} and
 * perform any necessary setup (temp directory, in-memory DB) in their own
 * {@code @Before} methods before calling {@code super.setUp()}.
 *
 * Test-naming convention: {@code methodUnderTest_scenario_expectedOutcome}.
 */
public abstract class AbstractPlayerProfileDAOTest {

    // The DAO under test - rebuilt fresh before every test by setUp().
    protected PlayerProfileDAO dao;

    // Concrete test classes provide their specific implementation here.
    protected abstract PlayerProfileDAO createDAO();

    @Before
    public void setUp() {
        dao = createDAO();
    }

    // =========================================================================
    // findById()
    // =========================================================================

    /**
     * A freshly initialised store has no profiles; any lookup must return empty.
     */
    @Test
    public void findById_emptyStore_returnsEmpty() {
        Optional<PlayerProfileDTO> result = dao.findById("human-1");

        assertFalse(result.isPresent());
    }

    /**
     * A null id must not throw; it must return empty (defensive guard).
     */
    @Test
    public void findById_nullId_returnsEmpty() {
        Optional<PlayerProfileDTO> result = dao.findById(null);

        assertFalse(result.isPresent());
    }

    /**
     * An id that has never been saved must return empty, even when other
     * profiles exist in the store.
     */
    @Test
    public void findById_unknownId_returnsEmpty() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));

        Optional<PlayerProfileDTO> result = dao.findById("human-99");

        assertFalse(result.isPresent());
    }

    /**
     * After saving a profile, findById with the same id must return a present
     * Optional whose fields exactly match what was saved.
     */
    @Test
    public void findById_savedProfile_returnsCorrectFields() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 5));

        PlayerProfileDTO found = dao.findById("human-1").orElseThrow();

        assertEquals("human-1", found.id);
        assertEquals("Alice",   found.name);
        assertEquals(5,         found.score);
    }

    /**
     * Id lookup must be case-sensitive: "Human-1" must not match "human-1".
     */
    @Test
    public void findById_idIsCaseSensitive_noMatchOnDifferentCase() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 0));

        Optional<PlayerProfileDTO> result = dao.findById("Human-1");

        assertFalse("findById must be case-sensitive", result.isPresent());
    }

    // =========================================================================
    // findByName()
    // =========================================================================

    /**
     * A freshly initialised store has no profiles; any name lookup returns empty.
     */
    @Test
    public void findByName_emptyStore_returnsEmpty() {
        Optional<PlayerProfileDTO> result = dao.findByName("Alice");

        assertFalse(result.isPresent());
    }

    /**
     * A null name must not throw; it must return empty.
     */
    @Test
    public void findByName_nullName_returnsEmpty() {
        Optional<PlayerProfileDTO> result = dao.findByName(null);

        assertFalse(result.isPresent());
    }

    /**
     * Name lookup must succeed with the exact original casing.
     */
    @Test
    public void findByName_exactCaseMatch_returnsProfile() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 2));

        assertTrue(dao.findByName("Alice").isPresent());
    }

    /**
     * The interface contract specifies case-insensitive matching.
     * "alice", "ALICE", and "Alice" must all find the same profile.
     */
    @Test
    public void findByName_differentCasing_stillMatches() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 2));

        assertTrue("lower-case should match",  dao.findByName("alice").isPresent());
        assertTrue("upper-case should match",  dao.findByName("ALICE").isPresent());
        assertTrue("mixed-case should match",  dao.findByName("aLiCe").isPresent());
    }

    /**
     * A name that was never saved must return empty.
     */
    @Test
    public void findByName_unknownName_returnsEmpty() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 2));

        assertFalse(dao.findByName("Bob").isPresent());
    }

    /**
     * findByName must return the correct profile when multiple profiles exist,
     * and must not accidentally return a different one.
     */
    @Test
    public void findByName_multipleProfiles_returnsOnlyTheMatchingOne() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-2", "Bob",   7));

        PlayerProfileDTO found = dao.findByName("Bob").orElseThrow();

        assertEquals("human-2", found.id);
        assertEquals("Bob",     found.name);
        assertEquals(7,         found.score);
    }

    // =========================================================================
    // findAll()
    // =========================================================================

    /**
     * An empty store must return an empty list, never null.
     */
    @Test
    public void findAll_emptyStore_returnsEmptyList() {
        List<PlayerProfileDTO> all = dao.findAll();

        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    /**
     * After saving two profiles, findAll must return exactly two entries.
     */
    @Test
    public void findAll_twoProfilesSaved_returnsBothProfiles() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-2", "Bob",   7));

        List<PlayerProfileDTO> all = dao.findAll();

        assertEquals(2, all.size());
    }

    /**
     * The list returned by findAll must contain all saved ids.
     */
    @Test
    public void findAll_savedProfiles_allIdsPresent() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 0));
        dao.save(new PlayerProfileDTO("human-2", "Bob",   0));
        dao.save(new PlayerProfileDTO("ai-1",    "CPU",   0));

        List<PlayerProfileDTO> all = dao.findAll();
        List<String> ids = all.stream().map(p -> p.id).toList();

        assertTrue(ids.contains("human-1"));
        assertTrue(ids.contains("human-2"));
        assertTrue(ids.contains("ai-1"));
    }

    /**
     * The list returned by findAll must be non-modifiable; direct mutation
     * must throw UnsupportedOperationException.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void findAll_returnedList_isUnmodifiable() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 0));

        List<PlayerProfileDTO> all = dao.findAll();
        all.add(new PlayerProfileDTO("human-2", "Bob", 0)); // must throw
    }

    // =========================================================================
    // save() — insert path
    // =========================================================================

    /**
     * Saving a profile and immediately querying by id must return it.
     * This is the basic round-trip test.
     */
    @Test
    public void save_newProfile_canBeRetrievedById() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 5));

        assertTrue(dao.findById("human-1").isPresent());
    }

    /**
     * Saving a profile with a score of zero must persist zero, not some default.
     */
    @Test
    public void save_profileWithZeroScore_persistsZeroExactly() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 0));

        int score = dao.findById("human-1").orElseThrow().score;

        assertEquals(0, score);
    }

    /**
     * A null profile must be rejected with an IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void save_nullProfile_throwsIllegalArgument() {
        dao.save(null);
    }

    /**
     * A profile with a null id must be rejected.
     */
    @Test(expected = IllegalArgumentException.class)
    public void save_profileWithNullId_throwsIllegalArgument() {
        dao.save(new PlayerProfileDTO(null, "Alice", 0));
    }

    /**
     * A profile with a blank id must be rejected.
     */
    @Test(expected = IllegalArgumentException.class)
    public void save_profileWithBlankId_throwsIllegalArgument() {
        dao.save(new PlayerProfileDTO("   ", "Alice", 0));
    }

    // =========================================================================
    // save() — upsert / overwrite path
    // =========================================================================

    /**
     * Saving a second profile with the same id must overwrite the original,
     * not create a duplicate. findAll must still return exactly one row.
     */
    @Test
    public void save_duplicateId_overwritesExistingProfile() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-1", "Alice", 10)); // same id, higher score

        List<PlayerProfileDTO> all = dao.findAll();
        assertEquals("Duplicate id must not create a second row", 1, all.size());
    }

    /**
     * After an upsert the stored score must reflect the most recent save.
     */
    @Test
    public void save_upsert_updatesScoreToLatestValue() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-1", "Alice", 10));

        int score = dao.findById("human-1").orElseThrow().score;

        assertEquals(10, score);
    }

    /**
     * Upsert must also update the name when it changes (e.g. player renames).
     */
    @Test
    public void save_upsert_updatesNameToLatestValue() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 3));
        dao.save(new PlayerProfileDTO("human-1", "Alicia", 3)); // name change

        String name = dao.findById("human-1").orElseThrow().name;

        assertEquals("Alicia", name);
    }

    /**
     * Multiple distinct profiles must coexist independently; saving one
     * must not affect another.
     */
    @Test
    public void save_multipleDistinctProfiles_eachRetrievableIndependently() {
        dao.save(new PlayerProfileDTO("human-1", "Alice", 5));
        dao.save(new PlayerProfileDTO("human-2", "Bob",   9));

        assertEquals(5, dao.findById("human-1").orElseThrow().score);
        assertEquals(9, dao.findById("human-2").orElseThrow().score);
    }
}
