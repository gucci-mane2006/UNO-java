package uno.java.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
 
import uno.java.controller.GameState;
import uno.java.core.*;
import uno.java.dao.GameSaveDAO;
import uno.java.dao.json.GameSaveJsonDAO;
import uno.java.dto.*;
import uno.java.player.*;

public class GameSaveManager implements GameSaveDAO {
    private final GameSaveDAO delegate;
    
    public GameSaveManager(Path savesDir) {
        this.delegate = new GameSaveJsonDAO(savesDir);
    }
    
    public GameSaveManager(GameSaveDAO delegate) {
        if (delegate == null) throw new IllegalArgumentException("delegate cannot be null");
        this.delegate = delegate;
    }
    
    // implementation
    @Override
    public void save(GameSaveDTO save) {
        delegate.save(save);
    }
 
    @Override
    public Optional<GameSaveDTO> load() {
        return delegate.load();
    }
 
    @Override
    public void delete() {
        delegate.delete();
    }
 
    @Override
    public boolean exists() {
        return delegate.exists();
    }
    
    // legacy GameController API
    public void save(GameState state, Deck deck) {
        save(toDTO(state, deck));
    }
 
    public GameSaveDTO loadOrNull() {
        return load().orElse(null);
    }
 
    public void deleteSave() {
        delete();
    }
 
    public boolean hasSave() {
        return exists();
    }
    
    // domain-conversion helpers (called by GameController.fromSave())
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
 
    public static Deck deckFromDTO(GameSaveDTO save) {
        List<Card> draw    = save.drawPile.stream()
                .map(GameSaveManager::cardFromDTO).collect(Collectors.toList());
        List<Card> discard = save.discardPile.stream()
                .map(GameSaveManager::cardFromDTO).collect(Collectors.toList());
        return Deck.fromPiles(draw, discard);
    }
    
    // private helpers - domain->dto conversion
    private GameSaveDTO toDTO(GameState state, Deck deck) {
        GameSaveDTO dto        = new GameSaveDTO();
        dto.roundNumber        = state.getRoundNumber();
        dto.currentPlayerIndex = state.getCurrentPlayerIndex();
        dto.clockwise          = state.isClockwise();
        dto.currentColor       = state.getCurrentColor().name();
        dto.topCard            = cardToDTO(state.getTopCard());
        dto.players            = state.getPlayers().stream()
                                      .map(this::playerToDTO)
                                      .collect(Collectors.toList());
        dto.drawPile           = deck.getDrawPileAsList().stream()
                                      .map(this::cardToDTO)
                                      .collect(Collectors.toList());
        dto.discardPile        = deck.getDiscardPileAsList().stream()
                                      .map(this::cardToDTO)
                                      .collect(Collectors.toList());
        return dto;
    }
 
    private CardDTO cardToDTO(Card card) {
        if (card == null) return null;
        return new CardDTO(card.getColor().name(), card.getType().name(), card.getNumber());
    }
 
    private PlayerSaveDTO playerToDTO(Player player) {
        PlayerSaveDTO dto = new PlayerSaveDTO();
        dto.id            = player.getId();
        dto.name          = player.getName();
        dto.score         = player.getScore();
        dto.playerType    = (player instanceof PlayerHuman) ? PlayerType.HUMAN : PlayerType.AI;
        dto.hand          = player.getHand().stream()
                                  .map(this::cardToDTO)
                                  .collect(Collectors.toList());
        return dto;
    }
}
