package uno.java.dao.derby;

public final class DerbySchema { // prev. "UNOTable"

    private DerbySchema() {}          // non-instantiable constants class

    // -------------------------------------------------------------------------
    // PLAYER — persistent win-count profiles
    // -------------------------------------------------------------------------

    public static final String CREATE_PLAYER =
            "CREATE TABLE PLAYER ("
            + "PLAYERID   VARCHAR(100) NOT NULL PRIMARY KEY, "
            + "PLAYERNAME VARCHAR(100) NOT NULL, "
            + "SCORE      INT          DEFAULT 0"
            + ")";

    // -------------------------------------------------------------------------
    // GAME_SAVE — single-slot mid-game snapshot stored as a JSON blob.
    //
    // SAVE_ID is always 1; an UPSERT (MERGE) keeps exactly one row alive.
    // -------------------------------------------------------------------------

    public static final String CREATE_GAME_SAVE =
            "CREATE TABLE GAME_SAVE ("
            + "SAVE_ID    INT          NOT NULL PRIMARY KEY, "
            + "SAVE_JSON  CLOB         NOT NULL"
            + ")";

    // -------------------------------------------------------------------------
    // CARD — optional card catalogue (not used by current DAO layer)
    // -------------------------------------------------------------------------

    public static final String CREATE_CARD =
            "CREATE TABLE CARD ("
            + "CARD_ID     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
            + "CARD_NUM    INT, "
            + "CARD_COLOUR VARCHAR(20), "
            + "CARD_TYPE   VARCHAR(50)"
            + ")";

    // -------------------------------------------------------------------------
    // GAME — optional game session log (not used by current DAO layer)
    // -------------------------------------------------------------------------

    public static final String CREATE_GAME =
            "CREATE TABLE GAME ("
            + "GAME_ID    INT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
            + "STARTED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ")";

    // -------------------------------------------------------------------------
    // PLAYER_HAND — optional hand-tracking (not used by current DAO layer)
    // -------------------------------------------------------------------------

    public static final String CREATE_PLAYER_HAND =
            "CREATE TABLE PLAYER_HAND ("
            + "HAND_ID   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
            + "GAME_ID   INT REFERENCES GAME(GAME_ID), "
            + "PLAYERID  VARCHAR(100) REFERENCES PLAYER(PLAYERID), "
            + "CARD_ID   INT REFERENCES CARD(CARD_ID)"
            + ")";

    // -------------------------------------------------------------------------
    // GAME_EVENT — optional event log (not used by current DAO layer)
    // -------------------------------------------------------------------------

    public static final String CREATE_GAME_EVENT =
            "CREATE TABLE GAME_EVENT ("
            + "EVENT_ID    INT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
            + "GAME_ID     INT       REFERENCES GAME(GAME_ID), "
            + "EVENT_TIME  TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "DESCRIPTION VARCHAR(500)"
            + ")";

    // -------------------------------------------------------------------------
    // UPSERT statements for active tables
    // -------------------------------------------------------------------------

    /**
     * Derby MERGE used to upsert a player profile.
     * Parameters: 1=PLAYERID, 2=PLAYERNAME, 3=SCORE.
     *
     * Derby requires the USING source to be a derived table produced by a
     * SELECT, not a bare VALUES clause. Wrapping VALUES in
     * "SELECT * FROM (VALUES (...)) AS t(cols)" satisfies that requirement.
     */
    public static final String UPDATE_PLAYER =
        "UPDATE PLAYER SET PLAYERNAME = ?, SCORE = ? WHERE PLAYERID = ?";

    public static final String INSERT_PLAYER =
        "INSERT INTO PLAYER (PLAYERID, PLAYERNAME, SCORE) VALUES (?, ?, ?)";

    /**
     * Derby MERGE used to upsert the single game-save row (SAVE_ID always = 1).
     * Parameters: 1=SAVE_JSON.
     *
     * Same Derby requirement: VALUES must be wrapped in a SELECT subquery.
     */
    public static final String UPDATE_GAME_SAVE =
        "UPDATE GAME_SAVE SET SAVE_JSON = ? WHERE SAVE_ID = 1";

    public static final String INSERT_GAME_SAVE =
        "INSERT INTO GAME_SAVE (SAVE_ID, SAVE_JSON) VALUES (1, ?)";
}