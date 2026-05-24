package uno.java.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import uno.java.core.*;
import uno.java.controller.*;
import uno.java.dto.*;
import uno.java.player.*;

public class GameSaveManager {
    private final Path saveFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public GameSaveManager(Path savesDir) {
        this.saveFile = savesDir.resolve("game.json");
    }
    
    /*
        PUBLIC API
    */
    
    public boolean hasSave() {
        return Files.exists(saveFile);
    }
    
    // Serialises current state to disk - called after every turn
    public void save(GameState state, Deck deck) {
        try {
            Files.createDirectories(saveFile.getParent());
            Files.writeString(saveFile, gson.toJson(toDTO(state, deck)));
        } 
        catch (IOException e) {
            System.err.println("[GameSaveManager] Could not write save: " + e.getMessage());
        }
    }
    
    // Returns persisted save - null if missing or corrupt
    public GameSaveDTO load() {
        if (!hasSave()) return null;
        
        try (Reader reader = Files.newBufferedReader(saveFile)) {
            return gson.fromJson(reader, GameSaveDTO.class);
        } 
        catch (IOException e) {
            System.err.println("[GameSaveManager] Could not read save: " + e.getMessage());
            return null;
        }
    }
    
    // Removes save file once game ends normally
    public void deleteSave() {
        try {
            Files.deleteIfExists(saveFile);
        } 
        catch (IOException e) {
            System.err.println("[GameSaveManager] Could not delete save: " + e.getMessage());
        }
    }
    
    /*
        DTO RECONSTRUCTION - CALLED BY GameController.fromSave()
    */
    
    // Card DTO reconstruction
    public static Card cardFromDTO(CardDTO dto) {
        if (dto == null) return null;
        
        return switch (Type.valueOf(dto.type)) {
            case NORMAL    -> Card.numberCard(Color.valueOf(dto.color), dto.number);
            case SKIP      -> Card.actionCard(Color.valueOf(dto.color), Type.SKIP);
            case REVERSE   -> Card.actionCard(Color.valueOf(dto.color), Type.REVERSE);
            case DRAW_TWO  -> Card.actionCard(Color.valueOf(dto.color), Type.DRAW_TWO);
            case WILD      -> Card.wildCard();
            case DRAW_FOUR -> Card.drawFourCard();
        };
    }
    
    // Deck (from the save DTO) reconstruction
    public static Deck deckFromDTO(GameSaveDTO save) {
        List<Card> draw     = save.drawPile.stream()
                .map(GameSaveManager::cardFromDTO).collect(Collectors.toList());
        List<Card> discard  = save.discardPile.stream()
                .map(GameSaveManager::cardFromDTO).collect(Collectors.toList());
        return Deck.fromPiles(draw, discard);
    }
    
    /*
        DOMAIN -> DTO CONVERSION (PRIVATE)
    */
    
    private GameSaveDTO toDTO(GameState state, Deck deck) {
        GameSaveDTO dto         = new GameSaveDTO();
        
        dto.roundNumber         = state.getRoundNumber();
        dto.currentPlayerIndex  = state.getCurrentPlayerIndex();
        dto.clockwise           = state.isClockwise();
        dto.currentColor        = state.getCurrentColor().name();
        dto.topCard             = cardToDTO(state.getTopCard());
        dto.players             = state.getPlayers().stream()
                                        .map(this::playerToDTO)
                                        .collect(Collectors.toList());
        dto.drawPile            = deck.getDrawPileAsList().stream()
                                        .map(this::cardToDTO)
                                        .collect(Collectors.toList());
        dto.discardPile         = deck.getDiscardPileAsList().stream()
                                        .map(this::cardToDTO)
                                        .collect(Collectors.toList());
        return dto;
    }
    
    private CardDTO cardToDTO(Card card) {
        if (card == null) return null;
        return new CardDTO(card.getColor().name(), card.getType().name(), card.getNumber());
    }
    
    private PlayerSaveDTO playerToDTO(Player player) {
        PlayerSaveDTO dto   = new PlayerSaveDTO();
        
        dto.id              = player.getId();
        dto.name            = player.getName();
        dto.score           = player.getScore();
        dto.playerType      = (player instanceof PlayerHuman) ? "HUMAN" : "AI";
        dto.hand            = player.getHand().stream()
                                    .map(this::cardToDTO)
                                    .collect(Collectors.toList());
        return dto;
    }
}
