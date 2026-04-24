package uno.java;

import java.nio.file.Path;
import java.util.*;

public class GameController {
    private final GameState state;
    private final Deck deck;
    private final ScoreManager scoreManager;
    private final GameSaveManager saveManager;
    private final ProfileRepository profileRepository;
    private final RuleEngine rules;
    private final RoundController roundController;

    public GameController(List<Player> players, int targetScore, Path saveDirectory, Path profilesFile) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("Game requires at least 2 players");
        this.state = new GameState(new ArrayList<>(players), targetScore);
        this.deck = new Deck();
        this.scoreManager = new ScoreManager();
        this.saveManager = new GameSaveManager(saveDirectory);
        this.profileRepository = new ProfileRepository(profilesFile);
        this.rules = new RuleEngine(state, deck);
        this.roundController = new RoundController(state, deck, rules);

        dealInitialHands();
    }

    private GameController(GameState state, Deck deck, GameSaveManager saveManager, ProfileRepository profileRepository) {
        this.state = Objects.requireNonNull(state);
        this.deck = Objects.requireNonNull(deck);
        this.scoreManager = new ScoreManager();
        this.saveManager = Objects.requireNonNull(saveManager);
        this.profileRepository = Objects.requireNonNull(profileRepository);
        this.rules = new RuleEngine(state, deck);
        this.roundController = new RoundController(state, deck, rules);
    }

    public static GameController fromSave(String saveId, Path saveDirectory, Path profilesFile, PlayerFactory playerFactory) {
        Objects.requireNonNull(saveId);
        Objects.requireNonNull(playerFactory);

        GameSaveManager saveManager = new GameSaveManager(saveDirectory);
        ProfileRepository profileRepository = new ProfileRepository(profilesFile);

        GameSaveData saveData = saveManager.loadGame(saveId)
                .orElseThrow(() -> new IllegalArgumentException("No save found with id: " + saveId));

        List<Player> players = new ArrayList<>();
        List<Card> excluded = new ArrayList<>();
        for (PlayerSaveData psd : saveData.getPlayers()) {
            Player player = playerFactory.create(psd);
            List<Card> rebuilt = psd.rebuildHand();
            rebuilt.forEach(player::addCard);
            players.add(player);
            excluded.addAll(rebuilt);
        }
        excluded.add(saveData.getTopCard().toCard());

        GameState state = new GameState(new ArrayList<>(players), saveData.getTargetScore());
        state.setCurrentPlayerIndex(saveData.getCurrentPlayerIndex());
        state.setClockwise(saveData.isClockwise());
        state.setTopCard(saveData.getTopCard().toCard());
        state.setCurrentColor(saveData.getCurrentColor());
        state.setPhase(saveData.getPhase());

        int savedRound = saveData.getRoundNumber();
        if (savedRound < 1) throw new IllegalArgumentException("Saved round number invalid: " + savedRound);
        while (state.getRoundNumber() < savedRound) state.incrementRound();

        Deck deck = new Deck(excluded);

        return new GameController(state, deck, saveManager, profileRepository);
    }

    public void startGame() {
        state.setPhase(GamePhase.IN_PROGRESS);
        while (!state.isGameOver()) {
            roundController.playRound();
            endRound();
            if (!state.isGameOver()) {
                state.incrementRound();
                dealInitialHands();
            }
        }
        endGame();
    }

    public void callUno(Player player) {
        if (player == null) return;
        player.markUnoCalled();
    }

    public void saveGame(String saveId) {
        Objects.requireNonNull(saveId);
        saveManager.saveGame(saveId, state);
    }

    public static List<GameSaveData> listSaves(Path saveDirectory) {
        return new GameSaveManager(saveDirectory).listSaves();
    }

    public void deleteSave(String saveId) {
        Objects.requireNonNull(saveId);
        saveManager.deleteSave(saveId);
    }

    public GameState getState() { return state; }

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

    private void commitProfilesToDisk() {
        for (Player player : state.getPlayers()) {
            PlayerProfile profile = profileRepository.loadProfile(player.getId())
                    .orElseGet(() -> new PlayerProfile(player.getId(), player.getName(), 0));
            profile.setName(player.getName());
            profile.setTotalScore(player.getScore());
            profileRepository.saveProfile(profile);
        }
    }

    private void dealInitialHands() {
        clearHands();
        final int initialHandSize = 7;
        for (int i = 0; i < initialHandSize; i++) {
            for (Player p : state.getPlayers()) {
                Optional<Card> c = deck.drawSafe();
                if (c.isEmpty()) throw new IllegalStateException("Not enough cards to deal initial hands");
                p.addCard(c.get());
            }
        }

        Card first;
        do {
            Optional<Card> opt = deck.drawSafe();
            if (opt.isEmpty()) throw new IllegalStateException("Not enough cards to draw initial top card");
            first = opt.get();
        } while (first.getType() == Type.DRAW_FOUR);

        deck.discard(first);
        state.setTopCard(first);
        if (first.getType().isWildType()) state.setCurrentColor(Color.RED);
        else state.setCurrentColor(first.getColor());
    }

    private void clearHands() {
        List<Card> toReturn = new ArrayList<>();
        for (Player p : state.getPlayers()) {
            List<Card> copy = new ArrayList<>(p.getHand());
            for (Card c : copy) {
                p.removeCard(c);
                toReturn.add(c);
            }
            p.clearUnoCalledFlag();
        }
        toReturn.forEach(deck::discard);
    }

    @FunctionalInterface
    public interface PlayerFactory { Player create(PlayerSaveData saveData); }
}