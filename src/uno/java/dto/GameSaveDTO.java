package uno.java.dto;

import java.util.List;
import java.util.ArrayList;

import uno.java.core.Color;
import uno.java.core.Type;

// Serialisable representation of a snapshot of a game in progress
// - written after every turn
public class GameSaveDTO {
    public int                  roundNumber;
    public int                  currentPlayerIndex;
    public boolean              clockwise;
    public String               currentColor;
    public CardDTO              topCard;
    public List<PlayerSaveDTO>  players;
    public List<CardDTO>        drawPile;
    public List<CardDTO>        discardPile;
    
    public GameSaveDTO() {}
    
    
    
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
 
        // roundNumber: Gson deserialises a missing int field as 0;
        // GameState.setRoundNumber() enforces >= 1
        if (roundNumber < 1)
            errors.add("roundNumber must be >= 1, got: " + roundNumber);
 
        // currentColor: must be a named Color that is not the WILD sentinel.
        // GameState.setCurrentColor() rejects null and WILD explicitly.
        if (currentColor == null) {
            errors.add("currentColor is null");
        } else {
            try {
                Color c = Color.valueOf(currentColor);
                if (c == Color.WILD)
                    errors.add("currentColor cannot be WILD");
            } catch (IllegalArgumentException e) {
                errors.add("currentColor is not a valid Color: \"" + currentColor + "\"");
            }
        }
 
        // topCard: GameState.setTopCard(null) throws immediately in fromSave()
        if (topCard == null)
            errors.add("topCard is null");
        else
            validateCard(topCard, "topCard", errors);
 
        // players: save.players.size() is called on the very next line after
        // validate() in Main, so a null or under-sized list fails immediately
        if (players == null) {
            errors.add("players list is null");
        } else if (players.size() < 2) {
            errors.add("players list must have at least 2 entries, got: " + players.size());
        } else {
            // currentPlayerIndex is only meaningful once player count is known
            if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size())
                errors.add("currentPlayerIndex " + currentPlayerIndex
                        + " is out of range for " + players.size() + " player(s)");
 
            for (int i = 0; i < players.size(); i++)
                validatePlayer(players.get(i), i, errors);
        }
 
        // drawPile / discardPile: SaveDTOMapper.deckFromDTO() calls .stream() on both
        if (drawPile == null) {
            errors.add("drawPile is null");
        } else {
            for (int i = 0; i < drawPile.size(); i++)
                validateCard(drawPile.get(i), "drawPile[" + i + "]", errors);
        }
 
        if (discardPile == null) {
            errors.add("discardPile is null");
        } else {
            for (int i = 0; i < discardPile.size(); i++)
                validateCard(discardPile.get(i), "discardPile[" + i + "]", errors);
        }
 
        return errors;
    }
    
    
    // helpers
    
    private static void validatePlayer(PlayerSaveDTO p, int index, List<String> errors) {
        if (p == null) {
            errors.add("players[" + index + "] is null");
            return;
        }
        String pfx = "players[" + index + "]";
 
        if (p.id == null || p.id.isBlank())
            errors.add(pfx + ".id is null or blank");
        if (p.name == null || p.name.isBlank())
            errors.add(pfx + ".name is null or blank");
        if (p.score < 0)
            errors.add(pfx + ".score must be >= 0, got: " + p.score);
        if (p.playerType == null)
            errors.add("... is null or not a recognised PlayerType value");
 
        if (p.hand == null) {
            errors.add(pfx + ".hand is null");
        } else {
            for (int i = 0; i < p.hand.size(); i++)
                validateCard(p.hand.get(i), pfx + ".hand[" + i + "]", errors);
        }
    }
 
    private static void validateCard(CardDTO card, String path, List<String> errors) {
        if (card == null) {
            errors.add(path + " is null");
            return;
        }
 
        Color color = null;
        if (card.color == null) {
            errors.add(path + ".color is null");
        } else {
            try {
                color = Color.valueOf(card.color);
            } catch (IllegalArgumentException e) {
                errors.add(path + ".color is not a valid Color: \"" + card.color + "\"");
            }
        }
 
        Type type = null;
        if (card.type == null) {
            errors.add(path + ".type is null");
        } else {
            try {
                type = Type.valueOf(card.type);
            } catch (IllegalArgumentException e) {
                errors.add(path + ".type is not a valid Type: \"" + card.type + "\"");
            }
        }
 
        // Cross-field rules - only evaluated when both enums resolved cleanly.
        if (color == null || type == null) return;
 
        boolean isWildType  = type == Type.WILD || type == Type.DRAW_FOUR;
        boolean isWildColor = color == Color.WILD;
 
        if (isWildType && !isWildColor)
            errors.add(path + ": " + type + " card must have color WILD, got: \"" + card.color + "\"");
        if (!isWildType && isWildColor)
            errors.add(path + ": color WILD is only valid for WILD and DRAW_FOUR cards");
 
        if (type == Type.NORMAL) {
            if (card.number == null)
                errors.add(path + ": NORMAL card must have a number");
            else if (card.number < 0 || card.number > 9)
                errors.add(path + ": NORMAL card number must be 0-9, got: " + card.number);
        } else if (card.number != null) {
            errors.add(path + ": non-NORMAL card (" + type + ") must not have a number, got: " + card.number);
        }
    }

}
