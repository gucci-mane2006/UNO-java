package uno.java;

import java.util.*;
import java.nio.file.Path;

public class GameController {
    private final GameState state;
    private final Deck deck;
    private final ScoreManager scoreManager;
    private final GameSaveManager saveManager;
    private final ProfileRepository profileRepository;
    private boolean unoCalledThisTurn;
    
    // Constructor
    public GameController(List<Player> players, int targetScore, Path saveDirectory, Path profilesFile) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("Game requires at least 2 players");
        
        this.state              = new GameState(players, targetScore);
        this.deck               = new Deck();
        this.scoreManager       = new ScoreManager();
        this.saveManager        = new GameSaveManager(saveDirectory);
        this.profileRepository  = new ProfileRepository(profilesFile);
        this.unoCalledThisTurn  = false;
        
        dealInitialHands();
    }
    

     // Private constructor used by fromSave() to restore a game from a save file
    private GameController(GameState state, Deck deck,
                           GameSaveManager saveManager, ProfileRepository profileRepository) {
        this.state             = state;
        this.deck              = deck;
        this.scoreManager      = new ScoreManager();
        this.saveManager       = saveManager;
        this.profileRepository = profileRepository;
        this.unoCalledThisTurn = false;
    }
    
    // Static factory - load from save
    public static GameController fromSave(String saveId,
            Path saveDirectory,
            Path profilesFile,
            PlayerFactory playerFactory
    ) {
        GameSaveManager   saveManager       = new GameSaveManager(saveDirectory);
        ProfileRepository profileRepository = new ProfileRepository(profilesFile);
 
        GameSaveData saveData = saveManager.loadGame(saveId)
                .orElseThrow(() -> new IllegalArgumentException("No save found with id: " + saveId));
 
        // Reconstruct Player instances via the caller-supplied factory,
        // then restore each player's hand from the save data
        List<Player> players = new ArrayList<>();
        for (PlayerSaveData psd : saveData.getPlayers()) {
            Player player = playerFactory.create(psd);
            for (Card card : psd.rebuildHand()) player.addCard(card);
            players.add(player);
        }
 
        // Build GameState with the restored player list and target score
        GameState state = new GameState(players, saveData.getTargetScore());
 
        // Restore state fields. roundNumber has no direct setter — we increment
        // from its initial value of 1 until we reach the saved round number
        state.setCurrentPlayerIndex(saveData.getCurrentPlayerIndex());
        state.setClockwise(saveData.isClockwise());
        state.setTopCard(saveData.getTopCard().toCard());
        state.setCurrentColor(saveData.getCurrentColor());
        state.setPhase(saveData.getPhase());
 
        for (int i = 1; i < saveData.getRoundNumber(); i++) state.incrementRound();
 
        // Fresh deck — we don't serialise the deck state, so the draw pile is
        // rebuilt from scratch. Cards already in players' hands came from the
        // save file, so in practice the deck will have slightly more cards than
        // it normally would at this point in the game. This is an acceptable
        // trade-off for simplicity
        Deck deck = new Deck();
 
        return new GameController(state, deck, saveManager, profileRepository);
    }


    /*
        PUBLIC API
    */

    public void startGame() {
        state.setPhase(GamePhase.IN_PROGRESS);
        
        while (!state.isGameOver()) {
            playRound();
            endRound();
            if (!state.isGameOver()) {
                state.incrementRound();
                dealInitialHands();
            }
        }
        
        endGame();
    }
    
    public void callUno() {
        this.unoCalledThisTurn = true; //uno called true

    }
    
    public void saveGame(String saveId) {
        saveManager.saveGame(saveId, state);
    }
    
    public static List<GameSaveData> listSaves(Path saveDirectory) {
        return new GameSaveManager(saveDirectory).listSaves();
    }

    public void deleteSave(String saveId) {
        saveManager.deleteSave(saveId);
    }

    public GameState getState() { return state; }

    /*
        PRIVATE LOGIC
    */

    // Round loop
    private void playRound() {
        state.setPhase(GamePhase.IN_PROGRESS);
        while (!state.isRoundOver()) playTurn();
        state.setPhase(GamePhase.ROUND_OVER);
    }
    
    private void playTurn() {
        Player current = state.getCurrentPlayer();
        Card played = current.takeTurn(state);
        
        // check for uno call before checkUno() consumes unoCalledThisTurn
        if (current instanceof PlayerHuman human 
                && human.getInputHandler().wasUnoCalled()) {
            callUno();
        }
        
        if (played == null) {
            Card drawn = deck.draw();
            current.addCard(drawn);
            if (state.isCardPlayable(drawn)) {
                current.removeCard(drawn);
                playCard(drawn, current);
                checkUno(current);
            } else {
                advancePlayer(1);
                unoCalledThisTurn = false;
            }
        } else {
            if (!current.removeCard(played)) throw new IllegalStateException("Player attempted to play a card they don't have");
            playCard(played, current);
            checkUno(current);
        }
    }
    
    private void playCard(Card card, Player player) {
        deck.discard(card);
        state.setTopCard(card);
        
        // Resolve color
        if (card.getType().isWildType()) {
            Color chosenColor = resolveWildColor(player);
            state.setCurrentColor(chosenColor);
        } else {
            state.setCurrentColor(card.getColor());
        }

        
        applyCardEffect(card, player);
        
        if (!state.isRoundOver()) advancePlayer(1);
    }

    // Card effects
    private void applyCardEffect(Card card, Player player) {
        switch (card.getType()) {
            case SKIP -> {
                // Skip the next player's turn by advancing an extra step
                advancePlayer(1);
            }
            case REVERSE -> {
                // Flip direction; in a two-player game this acts like a skip
                state.setClockwise(!state.isClockwise());
                if (state.getPlayerCount() == 2) advancePlayer(1);
            }
            case DRAW_TWO -> {
                // Next player draws two and loses their turn
                Player next = state.getNextPlayer();
                handleDraw(next, 2);
                advancePlayer(1);
            }
            case DRAW_FOUR -> {
                // Next player draws four and loses their turn
                Player next = state.getNextPlayer();
                handleDraw(next, 4);
                advancePlayer(1);
            }
            default -> {
                // NORMAL and WILD cards have no additional effect
            }
        }
    }

    // Player advancement
    private void advancePlayer(int steps) {
        int next = state.getNextPlayerIndex(steps);
        state.setCurrentPlayerIndex(next);
    }

    // Draw handling
    private void handleDraw(Player player, int count) {
        for (int i=0; i<count; i++) {
            Card draw = deck.draw();
            player.addCard(draw);
        }
    }

    // UNO check
    private void checkUno(Player player) {
        if (player.getHandSize() == 1 && !unoCalledThisTurn) {
            // TODO: event for uno penalty
            handleDraw(player, 2);
        }
        unoCalledThisTurn = false;
    }

    // Color resolution
    private Color resolveWildColor(Player player) {
        if (player instanceof PlayerAI ai) {
            return ai.getStrategy().chooseColor(player.getHand());
        }
        if (player instanceof PlayerHuman human) {
            return human.getInputHandler().selectColor();
        }
        return Color.RED;
    }

    // Round boundaries
    private void endRound() {
        state.getRoundWinner().ifPresent(winner -> {
            int points = scoreManager.calculateRoundScore(state.getPlayers(), winner);
            winner.addScore(points);
            commitProfilesToDisk();
        });
 
        clearHands();
    }
    
    private void endGame() {
        state.setPhase(GamePhase.GAME_OVER);
        commitProfilesToDisk();
    }
    
    // Profile persistence
    private void commitProfilesToDisk() {
        for (Player player : state.getPlayers()) {
            PlayerProfile profile = profileRepository.loadProfile(player.getId())
                    .orElseGet(() -> new PlayerProfile(player.getId(), player.getName(), 0));
 
            // Sync name in case it was changed mid-session
            profile.setName(player.getName());
 
            // Overwrite totalScore with the live score — the player's in-game
            // score IS their profile score (scores persist across rounds).
            profile.setTotalScore(player.getScore());
 
            profileRepository.saveProfile(profile);
        }
    }


    // Helpers
    private void dealInitialHands() {
        clearHands();
        
        for (int i = 0; i < 7; i++) 
            for (Player p : state.getPlayers()) p.addCard(deck.draw());
        
        // Draw the first card for the discard pile — skip wild draw-fours per UNO rules
        Card first;
        do { first = deck.draw(); }
        while (first.getType() == Type.DRAW_FOUR);
 
        deck.discard(first);
        state.setTopCard(first);
 
        // If the starting card is a wild, default color to RED until a player changes it
        if (first.getType().isWildType()) {
            state.setCurrentColor(Color.RED);
        } else {
            state.setCurrentColor(first.getColor());
        }
    }
    
    private void clearHands() {
        for (Player p : state.getPlayers()) {
            new ArrayList<>(p.getHand()).forEach(card -> {
                p.removeCard(card);
                deck.discard(card);   // return to deck
            });
        }
    }
    
    // PlayerFactory functional interface
    @FunctionalInterface
    public interface PlayerFactory {
        Player create(PlayerSaveData saveData);
    }
}
