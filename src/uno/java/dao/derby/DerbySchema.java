package uno.java.dao.derby;

public class DerbySchema { // previously "UNOTable"
    private DerbySchema() {}
    
    // -------------------------------------------------------------------------
    // PLAYER - persistent win-count profiles
    // -------------------------------------------------------------------------
 
    public static final String CREATE_PLAYER =
            "CREATE TABLE PLAYER ("
            + "PLAYERID   VARCHAR(100) NOT NULL PRIMARY KEY, "
            + "PLAYERNAME VARCHAR(100) NOT NULL, "
            + "SCORE      INT          DEFAULT 0"
            + ")";
 
    // -------------------------------------------------------------------------
    // GAME_SAVE - single-slot mid-game snapshot stored as a JSON blob.
    //
    // SAVE_ID is always 1; an UPSERT (MERGE) keeps exactly one row alive.
    // -------------------------------------------------------------------------
 
    public static final String CREATE_GAME_SAVE =
            "CREATE TABLE GAME_SAVE ("
            + "SAVE_ID    INT          NOT NULL PRIMARY KEY, "
            + "SAVE_JSON  CLOB         NOT NULL"
            + ")";
 
    // -------------------------------------------------------------------------
    // CARD - optional card catalogue (not used by current DAO layer)
    // -------------------------------------------------------------------------
 
    public static final String CREATE_CARD =
            "CREATE TABLE CARD ("
            + "CARD_ID     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
            + "CARD_NUM    INT, "
            + "CARD_COLOUR VARCHAR(20), "
            + "CARD_TYPE   VARCHAR(50)"
            + ")";
 
    // -------------------------------------------------------------------------
    // GAME - optional game session log (not used by current DAO layer)
    // -------------------------------------------------------------------------
 
    public static final String CREATE_GAME =
            "CREATE TABLE GAME ("
            + "GAME_ID    INT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
            + "STARTED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ")";
 
    // -------------------------------------------------------------------------
    // PLAYER_HAND - optional hand-tracking (not used by current DAO layer)
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
     */
    public static final String UPSERT_PLAYER =
            "MERGE INTO PLAYER AS target "
            + "USING (VALUES (?, ?, ?)) AS source (PLAYERID, PLAYERNAME, SCORE) "
            + "ON target.PLAYERID = source.PLAYERID "
            + "WHEN MATCHED THEN UPDATE SET target.PLAYERNAME = source.PLAYERNAME, "
            + "                             target.SCORE      = source.SCORE "
            + "WHEN NOT MATCHED THEN INSERT (PLAYERID, PLAYERNAME, SCORE) "
            + "                      VALUES (source.PLAYERID, source.PLAYERNAME, source.SCORE)";
 
    /**
     * Derby MERGE used to upsert the single game-save row (SAVE_ID always = 1).
     * Parameters: 1=SAVE_JSON.
     */
    public static final String UPSERT_GAME_SAVE =
            "MERGE INTO GAME_SAVE AS target "
            + "USING (VALUES (1, ?)) AS source (SAVE_ID, SAVE_JSON) "
            + "ON target.SAVE_ID = source.SAVE_ID "
            + "WHEN MATCHED     THEN UPDATE SET target.SAVE_JSON = source.SAVE_JSON "
            + "WHEN NOT MATCHED THEN INSERT (SAVE_ID, SAVE_JSON) "
            + "                      VALUES (source.SAVE_ID, source.SAVE_JSON)";
}
