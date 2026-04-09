const API_BASE = (() => {
    const origin = window.location.origin || "";
    const isLocalStaticHost = /^https?:\/\/(127\.0\.0\.1|localhost):55\d+$/i.test(origin);
    const isFileProtocol = window.location.protocol === "file:";
    return isFileProtocol || isLocalStaticHost ? "http://localhost:8080" : origin;
})();

const PRIVATE_ROOM_DEFAULTS = {
    maxPlayers: 6,
    rounds: 3,
    drawTimeSeconds: 80,
    wordChoiceCount: 3,
    hintCount: 2,
    privateRoom: true
};

const state = {
    roomId: null,
    playerId: null,
    playerName: null,
    room: null,
    stompClient: null,
    connected: false,
    drawing: false,
    currentWord: "",
    strokes: [],
    selectedLanguage: "English",
    invitedRoomId: null
};

const dom = {
    landingView: document.getElementById("landingView"),
    gameView: document.getElementById("gameView"),
    inviteNotice: document.getElementById("inviteNotice"),
    playerName: document.getElementById("playerName"),
    languageSelect: document.getElementById("languageSelect"),
    privateSettingsPanel: document.getElementById("privateSettingsPanel"),
    settingsLock: document.getElementById("settingsLock"),
    maxPlayers: document.getElementById("maxPlayers"),
    rounds: document.getElementById("rounds"),
    drawTimeSeconds: document.getElementById("drawTimeSeconds"),
    hintCount: document.getElementById("hintCount"),
    wordChoiceCount: document.getElementById("wordChoiceCount"),
    privateLanguageSelect: document.getElementById("privateLanguageSelect"),
    quickPlayBtn: document.getElementById("quickPlayBtn"),
    createRoomBtn: document.getElementById("createRoomBtn"),
    inviteOverlay: document.getElementById("inviteOverlay"),
    inviteRoomCode: document.getElementById("inviteRoomCode"),
    invitePlayerName: document.getElementById("invitePlayerName"),
    joinInviteBtn: document.getElementById("joinInviteBtn"),
    backHomeBtn: document.getElementById("backHomeBtn"),
    startGameBtn: document.getElementById("startGameBtn"),
    inviteBtn: document.getElementById("inviteBtn"),
    undoBtn: document.getElementById("undoBtn"),
    clearBtn: document.getElementById("clearBtn"),
    sendGuessBtn: document.getElementById("sendGuessBtn"),
    guessInput: document.getElementById("guessInput"),
    colorPicker: document.getElementById("colorPicker"),
    brushSize: document.getElementById("brushSize"),
    roomCode: document.getElementById("roomCode"),
    roomType: document.getElementById("roomType"),
    phase: document.getElementById("phase"),
    roundInfo: document.getElementById("roundInfo"),
    timer: document.getElementById("timer"),
    statusMessage: document.getElementById("statusMessage"),
    appStatus: document.getElementById("appStatus"),
    languageBadge: document.getElementById("languageBadge"),
    wordBanner: document.getElementById("wordBanner"),
    wordOptions: document.getElementById("wordOptions"),
    playerList: document.getElementById("playerList"),
    messageList: document.getElementById("messageList"),
    winnerChip: document.getElementById("winnerChip"),
    board: document.getElementById("board")
};

const ctx = dom.board.getContext("2d");
let previousPoint = null;

const readInputValue = (element, fallback = "") => {
    if (!element || typeof element.value !== "string") {
        return fallback;
    }
    return element.value;
};

const setAppStatus = (text, isError = false) => {
    dom.appStatus.textContent = text;
    dom.appStatus.classList.toggle("error", isError);
};

const showLandingView = () => {
    dom.gameView.classList.add("hidden");
    dom.landingView.classList.remove("hidden");
    dom.inviteOverlay.classList.add("hidden");
};

const showGameView = () => {
    dom.landingView.classList.add("hidden");
    dom.gameView.classList.remove("hidden");
};

const getLandingPlayerName = () => {
    const playerName = readInputValue(dom.playerName).trim();
    if (!playerName) {
        alert("Enter your name first.");
        throw new Error("Player name is required.");
    }
    state.selectedLanguage = readInputValue(dom.languageSelect, "English") || "English";
    return playerName;
};

const getInvitePlayerName = () => {
    const playerName = readInputValue(dom.invitePlayerName).trim();
    if (!playerName) {
        alert("Enter your name first.");
        throw new Error("Player name is required.");
    }
    return playerName;
};

const getPrivateSettings = () => ({
    maxPlayers: Number(readInputValue(dom.maxPlayers, String(PRIVATE_ROOM_DEFAULTS.maxPlayers))),
    rounds: Number(readInputValue(dom.rounds, String(PRIVATE_ROOM_DEFAULTS.rounds))),
    drawTimeSeconds: Number(readInputValue(dom.drawTimeSeconds, String(PRIVATE_ROOM_DEFAULTS.drawTimeSeconds))),
    wordChoiceCount: Number(readInputValue(dom.wordChoiceCount, String(PRIVATE_ROOM_DEFAULTS.wordChoiceCount))),
    hintCount: Number(readInputValue(dom.hintCount, String(PRIVATE_ROOM_DEFAULTS.hintCount))),
    privateRoom: true
});

const quickPlay = async () => {
    const playerName = getLandingPlayerName();
    const response = await fetch(`${API_BASE}/api/rooms/public`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerName })
    });
    const data = await parseResponse(response);
    await bootstrapSession(data);
    setAppStatus(`Joined public matchmaking in room ${data.roomId}.`);
};

const createPrivateRoom = async () => {
    const playerName = getLandingPlayerName();
    const response = await fetch(`${API_BASE}/api/rooms`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerName, settings: PRIVATE_ROOM_DEFAULTS })
    });
    const data = await parseResponse(response);
    await bootstrapSession(data);
    setAppStatus(`Private room ${data.roomId} created.`);
    updateInviteUrl(data.roomId);
};

const joinInvitedPrivateRoom = async () => {
    const playerName = getInvitePlayerName();
    const roomId = (state.invitedRoomId || "").trim().toUpperCase();
    if (!roomId) {
        throw new Error("Invite room is missing.");
    }

    const response = await fetch(`${API_BASE}/api/rooms/join`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerName, roomId })
    });
    const data = await parseResponse(response);
    if (dom.playerName) {
        dom.playerName.value = playerName;
    }
    await bootstrapSession(data);
    setAppStatus(`Joined private room ${data.roomId}.`);
    updateInviteUrl(data.roomId);
};

const bootstrapSession = async (data) => {
    state.roomId = data.roomId;
    state.playerId = data.playerId;
    state.playerName = data.playerName;
    state.room = data.room;
    dom.inviteOverlay.classList.add("hidden");
    clearMessages();
    addMessage(`Connected as ${data.playerName}.`, true);
    showGameView();
    renderRoom(data.room);
    await connectSocket();
};

const connectSocket = async () => {
    if (state.stompClient) {
        try {
            state.stompClient.disconnect();
        } catch (error) {
            console.warn(error);
        }
    }

    const socket = new SockJS(`${API_BASE}/ws`);
    state.stompClient = Stomp.over(socket);
    state.stompClient.debug = null;

    await new Promise((resolve, reject) => {
        state.stompClient.connect({}, () => {
            state.connected = true;
            subscribeToTopics();
            send("/app/room.connect", { roomId: state.roomId, playerId: state.playerId });
            setAppStatus("WebSocket connected.");
            resolve();
        }, (error) => {
            setAppStatus(`WebSocket failed: ${error}`, true);
            reject(error);
        });
    });
};

const subscribeToTopics = () => {
    state.stompClient.subscribe(`/topic/rooms/${state.roomId}`, (frame) => {
        handleEvent(JSON.parse(frame.body));
    });

    state.stompClient.subscribe(`/topic/rooms/${state.roomId}/player/${state.playerId}`, (frame) => {
        handleEvent(JSON.parse(frame.body));
    });
};

const handleEvent = (event) => {
    switch (event.type) {
        case "ROOM_PUBLIC":
        case "ROOM_STATE":
            renderRoom(event.payload);
            break;
        case "WORD_CHOICES":
            renderWordChoices(event.payload);
            break;
        case "DRAWER_WORD":
            state.currentWord = event.payload;
            renderWordChoices([]);
            renderWordBanner(event.payload);
            break;
        case "CANVAS_STROKE":
            drawStroke(event.payload);
            state.strokes.push(event.payload);
            break;
        case "CANVAS_RESET":
            state.strokes = Array.isArray(event.payload) ? event.payload : [];
            redrawCanvas();
            break;
        case "CHAT_MESSAGE":
            addMessage(`${event.payload.playerName}: ${event.payload.text}`);
            break;
        case "SYSTEM_MESSAGE":
            addMessage(event.payload, true);
            break;
        default:
            console.warn("Unhandled event", event);
    }
};

const send = (destination, payload) => {
    if (!state.connected || !state.stompClient) {
        setAppStatus("WebSocket is not connected yet.", true);
        return;
    }
    state.stompClient.send(destination, {}, JSON.stringify(payload));
};

const renderRoom = (room) => {
    state.room = room;
    dom.roomCode.textContent = room?.roomId || state.invitedRoomId || "-";
    dom.roomType.textContent = room?.roomType || "PRIVATE";
    dom.phase.textContent = room?.phase || "WAITING";
    dom.roundInfo.textContent = room ? `${room.currentRound} / ${room.totalRounds}` : "0 / 0";
    dom.statusMessage.textContent = room?.statusMessage || "Waiting for updates";
    dom.languageBadge.textContent = `Language: ${state.selectedLanguage}`;
    dom.winnerChip.textContent = room?.winnerName ? `Winner: ${room.winnerName}` : "";

    renderPrivateSettings(room);
    renderPlayers(room?.players || []);
    renderWordBanner(room?.maskedWord || "Waiting for round");
    updateButtons();
};

const renderPrivateSettings = (room) => {
    const isPrivate = room?.roomType === "PRIVATE";
    const me = room?.players?.find((player) => player.id === state.playerId);
    const canEdit = Boolean(isPrivate && me?.host && room.phase === "WAITING");

    dom.privateSettingsPanel.classList.toggle("hidden", !isPrivate);
    if (!isPrivate) {
        return;
    }

    const settings = room?.settings || PRIVATE_ROOM_DEFAULTS;
    dom.maxPlayers.value = String(settings.maxPlayers);
    dom.rounds.value = String(settings.rounds);
    dom.drawTimeSeconds.value = String(settings.drawTimeSeconds);
    dom.hintCount.value = String(settings.hintCount);
    dom.wordChoiceCount.value = String(settings.wordChoiceCount);
    dom.privateLanguageSelect.value = state.selectedLanguage || "English";
    dom.settingsLock.textContent = canEdit ? "Host controls" : "Host only";

    [
        dom.maxPlayers,
        dom.rounds,
        dom.drawTimeSeconds,
        dom.hintCount,
        dom.wordChoiceCount,
        dom.privateLanguageSelect
    ].forEach((element) => {
        element.disabled = !canEdit;
    });
};

const renderPlayers = (players) => {
    dom.playerList.innerHTML = "";
    players.forEach((player) => {
        const item = document.createElement("li");
        const left = document.createElement("div");
        const right = document.createElement("strong");
        const name = document.createElement("div");
        const meta = document.createElement("div");

        name.textContent = player.name + (player.id === state.playerId ? " (You)" : "");
        meta.className = "player-meta";
        meta.textContent = [
            player.host ? "host" : null,
            player.drawer ? "drawer" : null,
            player.ready ? "ready" : "auto",
            player.guessedCorrectly ? "guessed" : null
        ].filter(Boolean).join(" • ");
        left.append(name, meta);
        right.textContent = `${player.score} pts`;
        item.append(left, right);
        dom.playerList.appendChild(item);
    });
};

const renderWordChoices = (words) => {
    dom.wordOptions.innerHTML = "";
    words.forEach((word) => {
        const button = document.createElement("button");
        button.textContent = word;
        button.addEventListener("click", () => {
            send("/app/game.choose-word", { roomId: state.roomId, playerId: state.playerId, word });
        });
        dom.wordOptions.appendChild(button);
    });
};

const renderWordBanner = (text) => {
    dom.wordBanner.textContent = text || "Waiting for round";
};

const addMessage = (text, system = false) => {
    const item = document.createElement("li");
    item.textContent = system ? `[System] ${text}` : text;
    if (system) {
        item.style.borderLeft = "4px solid #EA4335";
    }
    dom.messageList.appendChild(item);
    dom.messageList.scrollTop = dom.messageList.scrollHeight;
};

const clearMessages = () => {
    dom.messageList.innerHTML = "";
};

const updateButtons = () => {
    const room = state.room;
    if (!room) {
        dom.startGameBtn.disabled = true;
        dom.inviteBtn.disabled = !state.invitedRoomId;
        dom.undoBtn.disabled = true;
        dom.clearBtn.disabled = true;
        dom.sendGuessBtn.disabled = true;
        dom.guessInput.disabled = true;
        return;
    }

    const me = room.players.find((player) => player.id === state.playerId);
    const isHost = me?.host;
    const isDrawer = me?.drawer;
    const isPlaying = room.phase === "PLAYING";
    const isWaiting = room.phase === "WAITING";
    const isPrivate = room.roomType === "PRIVATE";

    dom.startGameBtn.disabled = !(isPrivate && isHost && room.canStart && isWaiting);
    dom.inviteBtn.disabled = !isPrivate;
    dom.undoBtn.disabled = !(isDrawer && isPlaying);
    dom.clearBtn.disabled = !(isDrawer && isPlaying);
    dom.sendGuessBtn.disabled = !state.connected;
    dom.guessInput.disabled = !state.connected;
};

const drawStroke = (stroke) => {
    ctx.strokeStyle = stroke.color;
    ctx.lineWidth = stroke.size;
    ctx.lineCap = "round";
    ctx.beginPath();
    ctx.moveTo(stroke.x1, stroke.y1);
    ctx.lineTo(stroke.x2, stroke.y2);
    ctx.stroke();
};

const redrawCanvas = () => {
    ctx.clearRect(0, 0, dom.board.width, dom.board.height);
    state.strokes.forEach(drawStroke);
};

const isDrawer = () => state.room?.drawerPlayerId === state.playerId && state.room?.phase === "PLAYING";

const pointerPosition = (event) => {
    const rect = dom.board.getBoundingClientRect();
    const scaleX = dom.board.width / rect.width;
    const scaleY = dom.board.height / rect.height;
    return {
        x: (event.clientX - rect.left) * scaleX,
        y: (event.clientY - rect.top) * scaleY
    };
};

const startDrawing = (event) => {
    if (!isDrawer()) {
        return;
    }
    state.drawing = true;
    previousPoint = pointerPosition(event);
};

const moveDrawing = (event) => {
    if (!state.drawing || !previousPoint || !isDrawer()) {
        return;
    }
    const point = pointerPosition(event);
    const stroke = {
        x1: previousPoint.x,
        y1: previousPoint.y,
        x2: point.x,
        y2: point.y,
        color: readInputValue(dom.colorPicker, "#4285F4"),
        size: Number(readInputValue(dom.brushSize, "4"))
    };
    drawStroke(stroke);
    state.strokes.push(stroke);
    send("/app/game.draw", { roomId: state.roomId, playerId: state.playerId, stroke });
    previousPoint = point;
};

const stopDrawing = () => {
    state.drawing = false;
    previousPoint = null;
};

const sendGuess = () => {
    const text = readInputValue(dom.guessInput).trim();
    if (!text) {
        return;
    }
    send("/app/game.guess", { roomId: state.roomId, playerId: state.playerId, text });
    if (dom.guessInput) {
        dom.guessInput.value = "";
    }
};

const invitePrivateRoom = async () => {
    if (!state.roomId || state.room?.roomType !== "PRIVATE") {
        return;
    }
    const inviteUrl = `${window.location.origin}${window.location.pathname}?room=${encodeURIComponent(state.roomId)}`;
    try {
        await navigator.clipboard.writeText(inviteUrl);
        setAppStatus("Invite link copied to clipboard.");
    } catch (error) {
        prompt("Copy this invite link:", inviteUrl);
    }
};

const parseResponse = async (response) => {
    const contentType = response.headers.get("content-type") || "";
    const data = contentType.includes("application/json")
        ? await response.json()
        : { message: await response.text() };

    if (!response.ok) {
        throw new Error(data.message || data.error || "Request failed");
    }
    return data;
};

const checkBackend = async () => {
    setAppStatus("Frontend loaded. Checking backend...");
    try {
        const response = await fetch(`${API_BASE}/api/health`);
        const data = await parseResponse(response);
        if (data.status === "ok") {
            setAppStatus(`Backend reachable at ${API_BASE}.`);
        } else {
            setAppStatus("Backend responded unexpectedly.", true);
        }
    } catch (error) {
        setAppStatus(`Backend check failed: ${error.message}`, true);
    }
};

const showInviteRoomShell = (roomId) => {
    state.room = null;
    state.roomId = roomId;
    clearMessages();
    dom.privateSettingsPanel.classList.remove("hidden");
    renderPlayers([]);
    renderWordChoices([]);
    renderWordBanner("Join this private room to start drawing and guessing.");
    dom.roomCode.textContent = roomId;
    dom.roomType.textContent = "PRIVATE";
    dom.phase.textContent = "WAITING";
    dom.roundInfo.textContent = "0 / 0";
    dom.statusMessage.textContent = "You were invited to a private room.";
    dom.winnerChip.textContent = "";
    renderPrivateSettings({
        roomType: "PRIVATE",
        phase: "WAITING",
        settings: PRIVATE_ROOM_DEFAULTS,
        players: []
    });
    showGameView();
    updateButtons();
};

const restoreInviteContext = () => {
    const params = new URLSearchParams(window.location.search);
    const invitedRoomId = (params.get("room") || "").trim().toUpperCase();
    if (!invitedRoomId) {
        return;
    }

    state.invitedRoomId = invitedRoomId;
    dom.inviteRoomCode.textContent = invitedRoomId;
    showInviteRoomShell(invitedRoomId);
    dom.inviteOverlay.classList.remove("hidden");
};

const clearInviteContext = () => {
    state.invitedRoomId = null;
    if (dom.invitePlayerName) {
        dom.invitePlayerName.value = "";
    }
    dom.inviteOverlay.classList.add("hidden");
    const url = new URL(window.location.href);
    url.searchParams.delete("room");
    window.history.replaceState({}, "", url.toString());
};

const updateInviteUrl = (roomId) => {
    const url = new URL(window.location.href);
    url.searchParams.set("room", roomId);
    window.history.replaceState({}, "", url.toString());
};

const syncPrivateSettings = () => {
    const room = state.room;
    const me = room?.players?.find((player) => player.id === state.playerId);
    if (!room || room.roomType !== "PRIVATE" || !me?.host || room.phase !== "WAITING") {
        return;
    }

    state.selectedLanguage = readInputValue(dom.privateLanguageSelect, state.selectedLanguage || "English");
    send("/app/room.settings", {
        roomId: state.roomId,
        playerId: state.playerId,
        settings: getPrivateSettings()
    });
    renderRoom({
        ...room,
        settings: getPrivateSettings()
    });
};

setInterval(() => {
    if (!state.room?.roundEndsAt) {
        dom.timer.textContent = "--";
        return;
    }
    const diff = Math.max(0, state.room.roundEndsAt - Date.now());
    dom.timer.textContent = `${Math.ceil(diff / 1000)}s`;
}, 250);

dom.quickPlayBtn?.addEventListener("click", () => quickPlay().catch((error) => {
    setAppStatus(`Play failed: ${error.message}`, true);
    alert(error.message);
}));

dom.createRoomBtn?.addEventListener("click", () => createPrivateRoom().catch((error) => {
    setAppStatus(`Create room failed: ${error.message}`, true);
    alert(error.message);
}));

dom.joinInviteBtn?.addEventListener("click", () => joinInvitedPrivateRoom().catch((error) => {
    setAppStatus(`Join room failed: ${error.message}`, true);
    alert(error.message);
}));

dom.backHomeBtn?.addEventListener("click", () => {
    clearInviteContext();
    showLandingView();
});

dom.startGameBtn?.addEventListener("click", () => send("/app/room.start", { roomId: state.roomId, playerId: state.playerId }));
dom.inviteBtn?.addEventListener("click", invitePrivateRoom);
dom.undoBtn?.addEventListener("click", () => send("/app/game.undo", { roomId: state.roomId, playerId: state.playerId }));
dom.clearBtn?.addEventListener("click", () => send("/app/game.clear", { roomId: state.roomId, playerId: state.playerId }));
dom.sendGuessBtn?.addEventListener("click", sendGuess);
dom.guessInput?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        sendGuess();
    }
});
dom.invitePlayerName?.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        joinInvitedPrivateRoom().catch((error) => {
            setAppStatus(`Join room failed: ${error.message}`, true);
            alert(error.message);
        });
    }
});

[
    dom.maxPlayers,
    dom.rounds,
    dom.drawTimeSeconds,
    dom.hintCount,
    dom.wordChoiceCount
].forEach((element) => {
    element?.addEventListener("change", syncPrivateSettings);
});

dom.privateLanguageSelect?.addEventListener("change", () => {
    state.selectedLanguage = readInputValue(dom.privateLanguageSelect, "English");
    syncPrivateSettings();
    renderRoom(state.room);
});

dom.board?.addEventListener("pointerdown", startDrawing);
dom.board?.addEventListener("pointermove", moveDrawing);
dom.board?.addEventListener("pointerup", stopDrawing);
dom.board?.addEventListener("pointerleave", stopDrawing);

checkBackend();
restoreInviteContext();
