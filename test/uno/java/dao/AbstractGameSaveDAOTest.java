package uno.java.dao;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import uno.java.dto.CardDTO;
import uno.java.dto.GameSaveDTO;
import uno.java.dto.PlayerSaveDTO;

import static org.junit.Assert.*;

/**
 * Contract tests for {@link GameSaveDAO}.
 *
 * Covers the four interface methods — save, load, delete, exists — and the
 * single-slot / overwrite semantics described in the interface Javadoc.
 * Both the JSON and Derby implementations extend this class and run the full
 * suite, proving they are behaviourally equivalent.
 *
 * Subclasses supply a clean, empty DAO instance via {@link #createDAO()}.
 *
 * Test-naming convention: {@code methodUnderTest_scenario_expectedOutcome}.
 */
public abstract class AbstractGameSaveDAOTest {

    protected GameSaveDAO dao;

    protected abstract GameSaveDAO createDAO();

    @Before
    public void setUp() {
        dao = createDAO();
    }

    // =========================================================================
    // Shared helpers — build minimal but realistic GameSaveDTO instances
    // =========================================================================

    /**
     * Returns a minimal valid {@link GameSaveDTO} with one human player.
     * Every field that the DAO serialises / deserialises is populated so that
     * round-trip tests can assert on real values.
     */
    protected GameSaveDTO minimalSave() {
        GameSaveDTO dto         = new GameSaveDTO();
        dto.roundNumber         = 1;
        dto.currentPlayerIndex  = 0;
        dto.clockwise           = true;
        dto.currentColor        = "RED";
        dto.topCard             = new CardDTO("RED", "NORMAL", 7);
        dto.players             = new ArrayList<>(List.of(humanPlayerSave("human-1", "Alice")));
        dto.drawPile            = new ArrayList<>(List.of(new CardDTO("BLUE", "NORMAL", 3)));
        dto.discardPile         = new ArrayList<>(List.of(new CardDTO("RED",  "NORMAL", 7)));
        return dto;
    }

    /**
     * Returns a {@link GameSaveDTO} in round 3 with two players; used in
     * overwrite tests to confirm a distinct object replaced the previous one.
     */
    protected GameSaveDTO alternateSave() {
        GameSaveDTO dto         = new GameSaveDTO();
        dto.roundNumber         = 3;
        dto.currentPlayerIndex  = 1;
        dto.clockwise           = false;
        dto.currentColor        = "BLUE";
        dto.topCard             = new CardDTO("BLUE", "SKIP", null);
        dto.players             = new ArrayList<>(List.of(
                humanPlayerSave("human-1", "Alice"),
                humanPlayerSave("human-2", "Bob")));
        dto.drawPile            = new ArrayList<>();
        dto.discardPile         = new ArrayList<>(List.of(new CardDTO("BLUE", "SKIP", null)));
        return dto;
    }

    private PlayerSaveDTO humanPlayerSave(String id, String name) {
        PlayerSaveDTO p  = new PlayerSaveDTO();
        p.id             = id;
        p.name           = name;
        p.score          = 0;
        p.playerType     = "HUMAN";
        p.hand           = new ArrayList<>();
        return p;
    }

    // =========================================================================
    // exists()
    // =========================================================================

    /**
     * A freshly initialised store must report that no save exists.
     */
    @Test
    public void exists_emptyStore_returnsFalse() {
        assertFalse(dao.exists());
    }

    /**
     * After saving, exists() must return true.
     */
    @Test
    public void exists_afterSave_returnsTrue() {
        dao.save(minimalSave());

        assertTrue(dao.exists());
    }

    /**
     * After saving and then deleting, exists() must return false again.
     */
    @Test
    public void exists_afterSaveAndDelete_returnsFalse() {
        dao.save(minimalSave());
        dao.delete();

        assertFalse(dao.exists());
    }

    // =========================================================================
    // load()
    // =========================================================================

    /**
     * Loading from an empty store must return an empty Optional, never null.
     */
    @Test
    public void load_emptyStore_returnsEmpty() {
        Optional<GameSaveDTO> result = dao.load();

        assertFalse(result.isPresent());
    }

    /**
     * The basic round-trip: save then load must return an Optional that is present.
     */
    @Test
    public void load_afterSave_returnsPresent() {
        dao.save(minimalSave());

        assertTrue(dao.load().isPresent());
    }

    /**
     * Round-trip: the round number stored must be the same as the one loaded.
     */
    @Test
    public void load_afterSave_roundNumberMatchesSavedValue() {
        GameSaveDTO original = minimalSave(); // roundNumber = 1
        dao.save(original);

        int loaded = dao.load().orElseThrow().roundNumber;

        assertEquals(1, loaded);
    }

    /**
     * Round-trip: the current player index must survive serialisation.
     */
    @Test
    public void load_afterSave_currentPlayerIndexMatchesSavedValue() {
        GameSaveDTO original = minimalSave(); // currentPlayerIndex = 0
        dao.save(original);

        int loaded = dao.load().orElseThrow().currentPlayerIndex;

        assertEquals(0, loaded);
    }

    /**
     * Round-trip: the active color string must survive serialisation.
     */
    @Test
    public void load_afterSave_currentColorMatchesSavedValue() {
        dao.save(minimalSave()); // currentColor = "RED"

        String loaded = dao.load().orElseThrow().currentColor;

        assertEquals("RED", loaded);
    }

    /**
     * Round-trip: the play direction flag must survive serialisation.
     */
    @Test
    public void load_afterSave_clockwiseFlagMatchesSavedValue() {
        dao.save(minimalSave()); // clockwise = true

        boolean loaded = dao.load().orElseThrow().clockwise;

        assertTrue(loaded);
    }

    /**
     * Round-trip: the top-card DTO must survive serialisation — type and number
     * are both checked because the card may be a numbered or action card.
     */
    @Test
    public void load_afterSave_topCardMatchesSavedValue() {
        dao.save(minimalSave()); // topCard = RED NORMAL 7

        CardDTO loaded = dao.load().orElseThrow().topCard;

        assertEquals("RED",    loaded.color);
        assertEquals("NORMAL", loaded.type);
        assertEquals(Integer.valueOf(7), loaded.number);
    }

    /**
     * Round-trip: the player list must survive serialisation — count and first
     * player's id are checked.
     */
    @Test
    public void load_afterSave_playerListMatchesSavedValue() {
        dao.save(minimalSave()); // 1 player: human-1 / Alice

        List<PlayerSaveDTO> players = dao.load().orElseThrow().players;

        assertEquals(1,         players.size());
        assertEquals("human-1", players.get(0).id);
        assertEquals("Alice",   players.get(0).name);
    }

    /**
     * Round-trip: the draw pile card list must survive serialisation.
     */
    @Test
    public void load_afterSave_drawPileMatchesSavedValue() {
        dao.save(minimalSave()); // drawPile has one card: BLUE NORMAL 3

        List<CardDTO> drawPile = dao.load().orElseThrow().drawPile;

        assertEquals(1,      drawPile.size());
        assertEquals("BLUE", drawPile.get(0).color);
    }

    // =========================================================================
    // save() — overwrite / single-slot semantics
    // =========================================================================

    /**
     * The store holds at most one save. Calling save() twice must replace the
     * first snapshot, not create a second one.
     */
    @Test
    public void save_calledTwice_overwritesFirstSave() {
        dao.save(minimalSave());    // round 1
        dao.save(alternateSave()); // round 3

        int loaded = dao.load().orElseThrow().roundNumber;

        assertEquals("Second save must overwrite the first", 3, loaded);
    }

    /**
     * After overwriting, the player count from the latest save must be
     * persisted, not from the earlier one.
     */
    @Test
    public void save_overwrite_latestPlayerCountIsStored() {
        dao.save(minimalSave());    // 1 player
        dao.save(alternateSave()); // 2 players

        int playerCount = dao.load().orElseThrow().players.size();

        assertEquals(2, playerCount);
    }

    /**
     * A null save must be rejected with an IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void save_nullDto_throwsIllegalArgument() {
        dao.save(null);
    }

    // =========================================================================
    // delete()
    // =========================================================================

    /**
     * Calling delete() on an empty store must be a no-op (must not throw).
     */
    @Test
    public void delete_emptyStore_doesNotThrow() {
        dao.delete(); // must complete without exception
    }

    /**
     * Calling delete() twice in a row must be a no-op on the second call.
     */
    @Test
    public void delete_calledTwice_doesNotThrow() {
        dao.save(minimalSave());
        dao.delete();
        dao.delete(); // second call must also be silent
    }

    /**
     * After delete(), load() must return empty — the slot is cleared.
     */
    @Test
    public void delete_afterSave_loadReturnsEmpty() {
        dao.save(minimalSave());
        dao.delete();

        assertFalse(dao.load().isPresent());
    }

    /**
     * Save → delete → save again must work: the second save must be loadable.
     * This confirms delete truly clears the slot rather than putting it into a
     * broken state.
     */
    @Test
    public void delete_thenSaveAgain_newSaveIsLoadable() {
        dao.save(minimalSave());
        dao.delete();
        dao.save(alternateSave()); // round 3

        int loaded = dao.load().orElseThrow().roundNumber;

        assertEquals(3, loaded);
    }
}
