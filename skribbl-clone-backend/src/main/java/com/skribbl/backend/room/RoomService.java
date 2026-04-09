package com.skribbl.backend.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomService {

    private static final String WAITING = "WAITING";
    private static final String PICKING_WORD = "PICKING_WORD";
    private static final String PLAYING = "PLAYING";
    private static final String ROUND_END = "ROUND_END";
    private static final String GAME_OVER = "GAME_OVER";
    private static final String PUBLIC = "PUBLIC";
    private static final String PRIVATE = "PRIVATE";
    private static final int PUBLIC_MATCH_MIN_PLAYERS = 2;

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final List<String> wordBank = List.of(
        "apple", "backpack", "balloon", "bicycle", "bridge", "butterfly", "camel", "candle",
        "castle", "cat", "clock", "cloud", "dinosaur", "dragon", "elephant", "giraffe",
        "guitar", "hamburger", "helmet", "island", "jellyfish", "kangaroo", "lantern",
        "lighthouse", "mountain", "octopus", "panda", "pizza", "rainbow", "robot", "rocket",
        "scooter", "shark", "spaceship", "sunflower", "telescope", "treasure", "umbrella",
        "volcano", "whale"
    );

    public RoomService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public RoomSessionResponse createRoom(CreateRoomRequest request) {
        String roomId = generateRoomId();
        RoomSettingsRequest settings = sanitizePrivateSettings(request.settings());
        GameRoom room = new GameRoom(roomId, settings, PRIVATE, false);
        PlayerState host = room.addPlayer(request.playerName());
        room.hostPlayerId = host.id;
        room.statusMessage = "Share the room code with friends to play privately.";
        rooms.put(room.id, room);
        return new RoomSessionResponse(room.id, host.id, host.name, toView(room, host.id));
    }

    public RoomSessionResponse joinPublicMatch(String playerName) {
        GameRoom room = findJoinablePublicRoom();
        synchronized (room) {
            PlayerState player = room.addPlayer(playerName);
            room.handleLateJoin(player);
            room.statusMessage = room.phase.equals(WAITING)
                ? room.players.size() < PUBLIC_MATCH_MIN_PLAYERS
                    ? "Waiting for more public players to join matchmaking."
                    : "Public match found. Starting game..."
                : player.name + " joined the public game and will draw in a later turn.";
            broadcastRoom(room);

            if (room.players.size() >= PUBLIC_MATCH_MIN_PLAYERS && WAITING.equals(room.phase)) {
                autoStartPublicGame(room);
            }

            return new RoomSessionResponse(room.id, player.id, player.name, toView(room, player.id));
        }
    }

    public RoomSessionResponse joinRoom(JoinRoomRequest request) {
        GameRoom room = getRoom(request.roomId());
        synchronized (room) {
            if (PUBLIC.equals(room.roomType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public rooms are joined through matchmaking.");
            }
            if (room.players.size() >= room.settings.maxPlayers()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room is full.");
            }
            if (GAME_OVER.equals(room.phase)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game already finished.");
            }
            PlayerState player = room.addPlayer(request.playerName());
            room.handleLateJoin(player);
            room.statusMessage = WAITING.equals(room.phase)
                ? player.name + " joined the private room."
                : player.name + " joined mid-game and will draw after the current players finish.";
            broadcastRoom(room);
            return new RoomSessionResponse(room.id, player.id, player.name, toView(room, player.id));
        }
    }

    public RoomView getRoomView(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            requirePlayer(room, playerId);
            return toView(room, playerId);
        }
    }

    public void connect(String roomId, String playerId, String sessionId) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            PlayerState player = requirePlayer(room, playerId);
            player.sessionId = sessionId;
            sendToPlayer(room.id, player.id, new RoomEvent("ROOM_STATE", toView(room, player.id)));
            sendToPlayer(room.id, player.id, new RoomEvent("CANVAS_RESET", room.canvas));
            if (PICKING_WORD.equals(room.phase) && Objects.equals(player.id, room.drawerPlayerId)) {
                sendToPlayer(room.id, player.id, new RoomEvent("WORD_CHOICES", room.wordChoices));
            }
        }
    }

    public void disconnect(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        for (GameRoom room : rooms.values()) {
            synchronized (room) {
                PlayerState disconnectedPlayer = room.findBySessionId(sessionId);
                if (disconnectedPlayer == null) {
                    continue;
                }

                removePlayerFromRoom(room, disconnectedPlayer);
                return;
            }
        }
    }

    public void toggleReady(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ready toggling is no longer used.");
        }
    }

    public void updatePrivateSettings(String roomId, String playerId, RoomSettingsRequest settings) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            if (PUBLIC.equals(room.roomType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public room settings are fixed.");
            }
            if (!WAITING.equals(room.phase)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settings can only be changed before the game starts.");
            }
            if (!Objects.equals(playerId, room.hostPlayerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can change room settings.");
            }

            room.settings = sanitizePrivateSettings(settings);
            room.statusMessage = "Private room settings updated.";
            broadcastRoom(room);
        }
    }

    public void startGame(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            if (PUBLIC.equals(room.roomType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Public matches start automatically.");
            }
            if (!Objects.equals(playerId, room.hostPlayerId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can start the game.");
            }
            if (room.players.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least 2 players are required.");
            }
            initializeGame(room, false);
        }
    }

    public void chooseWord(String roomId, String playerId, String word) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            requireActiveDrawer(room, playerId, PICKING_WORD);
            if (!room.wordChoices.contains(word)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of the offered words.");
            }

            room.selectedWord = word.trim();
            room.revealedIndexes.clear();
            room.phase = PLAYING;
            room.roundEndsAt = Instant.now().plusSeconds(room.settings.drawTimeSeconds()).toEpochMilli();
            room.statusMessage = room.players.get(playerId).name + " is drawing now.";
            room.canvas.clear();
            room.players.values().forEach(player -> player.guessedCorrectly = false);

            scheduleRoundTasks(room);
            broadcast(new RoomEvent("CANVAS_RESET", room.canvas), room.id);
            broadcastRoom(room);
            sendToPlayer(room.id, playerId, new RoomEvent("DRAWER_WORD", room.selectedWord));
        }
    }

    public void addStroke(String roomId, String playerId, StrokeData stroke) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            requireActiveDrawer(room, playerId, PLAYING);
            room.canvas.add(stroke);
            broadcast(new RoomEvent("CANVAS_STROKE", stroke), room.id);
        }
    }

    public void clearCanvas(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            requireActiveDrawer(room, playerId, PLAYING);
            room.canvas.clear();
            broadcast(new RoomEvent("CANVAS_RESET", room.canvas), room.id);
        }
    }

    public void undoLastStroke(String roomId, String playerId) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            requireActiveDrawer(room, playerId, PLAYING);
            if (!room.canvas.isEmpty()) {
                room.canvas.remove(room.canvas.size() - 1);
                broadcast(new RoomEvent("CANVAS_RESET", room.canvas), room.id);
            }
        }
    }

    public void submitGuess(String roomId, String playerId, String rawText) {
        GameRoom room = getRoom(roomId);
        synchronized (room) {
            PlayerState player = requirePlayer(room, playerId);
            String text = rawText == null ? "" : rawText.trim();
            if (text.isBlank()) {
                return;
            }

            if (!PLAYING.equals(room.phase)) {
                broadcastChat(room.id, player, text);
                return;
            }
            if (Objects.equals(room.drawerPlayerId, playerId) || player.guessedCorrectly) {
                return;
            }

            if (normalize(text).equals(normalize(room.selectedWord))) {
                player.guessedCorrectly = true;
                int secondsLeft = Math.max(1, (int) Math.ceil((room.roundEndsAt - Instant.now().toEpochMilli()) / 1000.0));
                player.score += 100 + (secondsLeft * 2);
                room.players.get(room.drawerPlayerId).score += 40;
                finishRound(room, player.name + " guessed the word: " + room.selectedWord);
                return;
            }

            broadcastChat(room.id, player, text);
        }
    }

    private RoomSettingsRequest sanitizePrivateSettings(RoomSettingsRequest settings) {
        return new RoomSettingsRequest(
            settings.maxPlayers(),
            settings.rounds(),
            settings.drawTimeSeconds(),
            settings.wordChoiceCount(),
            settings.hintCount(),
            true
        );
    }

    private RoomSettingsRequest publicSettings() {
        return new RoomSettingsRequest(Integer.MAX_VALUE, 2, 60, 3, 2, false);
    }

    private GameRoom findJoinablePublicRoom() {
        prunePublicRooms();
        GameRoom bestMatch = selectBestPublicRoom(rooms.values());
        if (bestMatch != null) {
            return bestMatch;
        }
        String roomId = generateRoomId();
        GameRoom room = new GameRoom(roomId, publicSettings(), PUBLIC, true);
        room.statusMessage = "Public queue created. Waiting for players...";
        rooms.put(room.id, room);
        return room;
    }

    private GameRoom selectBestPublicRoom(Collection<GameRoom> allRooms) {
        return allRooms.stream()
            .filter(GameRoom::isJoinablePublicRoom)
            .max(Comparator
                .comparingInt(GameRoom::publicPriority)
                .thenComparingInt(GameRoom::playerCount)
                .thenComparingLong(GameRoom::createdAt))
            .orElse(null);
    }

    private void prunePublicRooms() {
        List<String> roomIdsToRemove = rooms.values().stream()
            .filter(GameRoom::shouldDeletePublicRoom)
            .map(room -> room.id)
            .toList();

        roomIdsToRemove.forEach(rooms::remove);
    }

    private void autoStartPublicGame(GameRoom room) {
        initializeGame(room, true);
    }

    private void removePlayerFromRoom(GameRoom room, PlayerState disconnectedPlayer) {
        boolean wasDrawer = Objects.equals(room.drawerPlayerId, disconnectedPlayer.id);
        boolean wasHost = Objects.equals(room.hostPlayerId, disconnectedPlayer.id);

        room.players.remove(disconnectedPlayer.id);
        room.drawerOrder.remove(disconnectedPlayer.id);

        if (room.players.isEmpty()) {
            cancelTasks(room);
            rooms.remove(room.id);
            return;
        }

        if (wasHost) {
            room.hostPlayerId = room.players.keySet().iterator().next();
        }

        if (WAITING.equals(room.phase)) {
            room.statusMessage = disconnectedPlayer.name + " left the room.";
            broadcastRoom(room);
            return;
        }

        if (room.players.size() < 2) {
            resetToWaiting(room, disconnectedPlayer.name + " left. Waiting for more players to continue.");
            broadcastRoom(room);
            return;
        }

        if (!room.drawerOrder.isEmpty()) {
            room.drawerIndex = Math.min(room.drawerIndex, room.drawerOrder.size() - 1);
        }

        if (wasDrawer) {
            if (room.drawerIndex >= room.drawerOrder.size()) {
                room.drawerIndex = 0;
            }
            room.turnNumber = Math.max(1, room.turnNumber - 1);
            cancelTasks(room);
            room.statusMessage = disconnectedPlayer.name + " left during their turn.";
            advanceTurn(room);
            return;
        }

        room.statusMessage = disconnectedPlayer.name + " left the room.";
        broadcastRoom(room);
    }

    private void resetToWaiting(GameRoom room, String statusMessage) {
        cancelTasks(room);
        room.phase = WAITING;
        room.drawerOrder = new ArrayList<>(room.players.keySet());
        room.wordChoices = new ArrayList<>();
        room.drawerPlayerId = null;
        room.selectedWord = null;
        room.currentRound = 0;
        room.turnNumber = 0;
        room.drawerIndex = 0;
        room.roundEndsAt = 0L;
        room.winnerName = null;
        room.canvas.clear();
        room.revealedIndexes.clear();
        room.players.values().forEach(player -> player.guessedCorrectly = false);
        room.statusMessage = statusMessage;
        if (PUBLIC.equals(room.roomType) && room.players.size() >= PUBLIC_MATCH_MIN_PLAYERS) {
            autoStartPublicGame(room);
        }
    }

    private void initializeGame(GameRoom room, boolean autoReadyEveryone) {
        room.currentRound = 1;
        room.turnNumber = 1;
        room.drawerIndex = 0;
        room.drawerOrder = new ArrayList<>(room.players.keySet());
        room.winnerName = null;
        room.players.values().forEach(player -> {
            player.score = 0;
            player.guessedCorrectly = false;
            player.ready = autoReadyEveryone || player.id.equals(room.hostPlayerId);
        });
        prepareRound(room);
    }

    private void prepareRound(GameRoom room) {
        cancelTasks(room);
        room.phase = PICKING_WORD;
        room.roundEndsAt = 0L;
        room.selectedWord = null;
        room.canvas.clear();
        room.revealedIndexes.clear();
        room.drawerPlayerId = room.drawerOrder.get(room.drawerIndex);
        room.wordChoices = chooseWords(room.settings.wordChoiceCount());
        room.statusMessage = room.players.get(room.drawerPlayerId).name + " is choosing a word.";

        broadcast(new RoomEvent("CANVAS_RESET", room.canvas), room.id);
        broadcastRoom(room);
        sendToPlayer(room.id, room.drawerPlayerId, new RoomEvent("WORD_CHOICES", room.wordChoices));
    }

    private void scheduleRoundTasks(GameRoom room) {
        cancelTasks(room);
        room.roundTask = scheduler.schedule(() -> {
            synchronized (room) {
                finishRound(room, "Time is up. The word was " + room.selectedWord + ".");
            }
        }, room.settings.drawTimeSeconds(), TimeUnit.SECONDS);

        if (room.settings.hintCount() > 0) {
            int interval = Math.max(5, room.settings.drawTimeSeconds() / (room.settings.hintCount() + 1));
            for (int hint = 1; hint <= room.settings.hintCount(); hint++) {
                long delay = (long) interval * hint;
                ScheduledFuture<?> task = scheduler.schedule(() -> {
                    synchronized (room) {
                        revealHint(room);
                    }
                }, delay, TimeUnit.SECONDS);
                room.hintTasks.add(task);
            }
        }
    }

    private void revealHint(GameRoom room) {
        if (!PLAYING.equals(room.phase) || room.selectedWord == null) {
            return;
        }

        for (int index = 0; index < room.selectedWord.length(); index++) {
            if (room.selectedWord.charAt(index) != ' ' && !room.revealedIndexes.contains(index)) {
                room.revealedIndexes.add(index);
                room.statusMessage = "A hint was revealed.";
                broadcastRoom(room);
                return;
            }
        }
    }

    private void finishRound(GameRoom room, String message) {
        cancelTasks(room);
        room.phase = ROUND_END;
        room.roundEndsAt = 0L;
        room.statusMessage = message;
        broadcast(new RoomEvent("SYSTEM_MESSAGE", message), room.id);
        broadcastRoom(room);

        room.roundTask = scheduler.schedule(() -> {
            synchronized (room) {
                advanceTurn(room);
            }
        }, 3, TimeUnit.SECONDS);
    }

    private void advanceTurn(GameRoom room) {
        room.drawerIndex++;
        room.turnNumber++;
        if (room.drawerIndex >= room.drawerOrder.size()) {
            room.drawerIndex = 0;
            room.currentRound++;
        }

        if (room.currentRound > room.settings.rounds()) {
            room.phase = GAME_OVER;
            room.winnerName = room.players.values().stream()
                .max(Comparator.comparingInt(player -> player.score))
                .map(player -> player.name)
                .orElse("No winner");
            room.statusMessage = room.winnerName + " wins the game.";
            broadcastRoom(room);
            return;
        }

        prepareRound(room);
    }

    private void broadcastRoom(GameRoom room) {
        broadcast(new RoomEvent("ROOM_PUBLIC", toView(room, null)), room.id);
        for (PlayerState player : room.players.values()) {
            sendToPlayer(room.id, player.id, new RoomEvent("ROOM_STATE", toView(room, player.id)));
        }
    }

    private void broadcastChat(String roomId, PlayerState player, String text) {
        broadcast(new RoomEvent("CHAT_MESSAGE", Map.of(
            "playerId", player.id,
            "playerName", player.name,
            "text", text
        )), roomId);
    }

    private void sendToPlayer(String roomId, String playerId, RoomEvent event) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/player/" + playerId, event);
    }

    private void broadcast(RoomEvent event, String roomId) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, event);
    }

    private List<String> chooseWords(int amount) {
        List<String> words = wordBank.stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));

        if (words.size() < amount) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Not enough words configured.");
        }

        java.util.Collections.shuffle(words);
        return new ArrayList<>(words.subList(0, amount));
    }

    private GameRoom getRoom(String roomId) {
        GameRoom room = rooms.get(roomId.toUpperCase(Locale.ROOT));
        if (room == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found.");
        }
        return room;
    }

    private PlayerState requirePlayer(GameRoom room, String playerId) {
        PlayerState player = room.players.get(playerId);
        if (player == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found.");
        }
        return player;
    }

    private void requireActiveDrawer(GameRoom room, String playerId, String requiredPhase) {
        if (!requiredPhase.equals(room.phase)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action not allowed right now.");
        }
        if (!Objects.equals(room.drawerPlayerId, playerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the current drawer can do that.");
        }
    }

    private RoomView toView(GameRoom room, String viewerPlayerId) {
        List<PlayerView> players = room.players.values().stream()
            .map(player -> new PlayerView(
                player.id,
                player.name,
                player.score,
                player.ready,
                player.id.equals(room.hostPlayerId),
                player.id.equals(room.drawerPlayerId),
                player.guessedCorrectly
            ))
            .toList();

        String maskedWord = "";
        if (room.selectedWord != null) {
            maskedWord = Objects.equals(viewerPlayerId, room.drawerPlayerId)
                ? room.selectedWord
                : maskWord(room.selectedWord, room.revealedIndexes);
        }

        return new RoomView(
            room.id,
            room.phase,
            room.roomType,
            room.settings,
            players,
            room.hostPlayerId,
            room.drawerPlayerId,
            room.drawerPlayerId == null ? null : room.players.get(room.drawerPlayerId).name,
            room.currentRound,
            room.settings.rounds(),
            room.turnNumber,
            maskedWord,
            room.roundEndsAt,
            PRIVATE.equals(room.roomType) && WAITING.equals(room.phase) && room.players.size() > 1,
            room.statusMessage,
            room.winnerName
        );
    }

    private String maskWord(String word, Set<Integer> revealedIndexes) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            char current = word.charAt(index);
            if (current == ' ') {
                builder.append("  ");
            } else if (revealedIndexes.contains(index)) {
                builder.append(current).append(' ');
            } else {
                builder.append("_ ");
            }
        }
        return builder.toString().trim();
    }

    private void cancelTasks(GameRoom room) {
        if (room.roundTask != null) {
            room.roundTask.cancel(false);
            room.roundTask = null;
        }
        room.hintTasks.forEach(task -> task.cancel(false));
        room.hintTasks.clear();
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String generateRoomId() {
        String roomId;
        do {
            roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        } while (rooms.containsKey(roomId));
        return roomId;
    }

    private static final class GameRoom {
        private final String id;
        private RoomSettingsRequest settings;
        private final String roomType;
        private final boolean autoStart;
        private final long createdAt;
        private final Map<String, PlayerState> players = new LinkedHashMap<>();
        private final List<StrokeData> canvas = new ArrayList<>();
        private final Set<Integer> revealedIndexes = new LinkedHashSet<>();
        private final List<ScheduledFuture<?>> hintTasks = new ArrayList<>();

        private List<String> drawerOrder = new ArrayList<>();
        private List<String> wordChoices = new ArrayList<>();
        private String hostPlayerId;
        private String drawerPlayerId;
        private String selectedWord;
        private String phase = WAITING;
        private String winnerName;
        private String statusMessage = "Waiting for players to join.";
        private int currentRound;
        private int turnNumber;
        private int drawerIndex;
        private long roundEndsAt;
        private ScheduledFuture<?> roundTask;

        private GameRoom(String id, RoomSettingsRequest settings, String roomType, boolean autoStart) {
            this.id = id.toUpperCase(Locale.ROOT);
            this.settings = settings;
            this.roomType = roomType;
            this.autoStart = autoStart;
            this.createdAt = System.currentTimeMillis();
        }

        private PlayerState addPlayer(String name) {
            String trimmedName = name == null ? "" : name.trim();
            if (trimmedName.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player name is required.");
            }

            PlayerState player = new PlayerState(UUID.randomUUID().toString(), trimmedName);
            player.ready = true;
            players.put(player.id, player);
            if (hostPlayerId == null) {
                hostPlayerId = player.id;
            }
            return player;
        }

        private boolean isJoinablePublicRoom() {
            return PUBLIC.equals(roomType)
                && !players.isEmpty()
                && !GAME_OVER.equals(phase);
        }

        private int publicPriority() {
            return isActivePublicGame() ? 2 : 1;
        }

        private int playerCount() {
            return players.size();
        }

        private long createdAt() {
            return createdAt;
        }

        private boolean isActivePublicGame() {
            return PUBLIC.equals(roomType)
                && (PICKING_WORD.equals(phase) || PLAYING.equals(phase) || ROUND_END.equals(phase));
        }

        private boolean shouldDeletePublicRoom() {
            return PUBLIC.equals(roomType)
                && (players.isEmpty() || GAME_OVER.equals(phase));
        }

        private void handleLateJoin(PlayerState player) {
            if (!WAITING.equals(phase) && !drawerOrder.contains(player.id)) {
                drawerOrder.add(player.id);
            }
            if (!WAITING.equals(phase)) {
                player.ready = true;
                player.guessedCorrectly = false;
            }
        }

        private PlayerState findBySessionId(String sessionId) {
            return players.values().stream()
                .filter(player -> sessionId.equals(player.sessionId))
                .findFirst()
                .orElse(null);
        }
    }

    private static final class PlayerState {
        private final String id;
        private final String name;
        private int score;
        private boolean ready;
        private boolean guessedCorrectly;
        private String sessionId;

        private PlayerState(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
