# Skribbl Clone MVP

This project implements the assignment as a Spring Boot + STOMP backend with a plain HTML, CSS, and JavaScript frontend.

## Stack

- Backend: Java 17, Spring Boot, Spring WebSocket (STOMP)
- Real-time communication: SockJS + STOMP
- Frontend: HTML, CSS, JavaScript
- Storage: In-memory room state and in-memory word list

## What is implemented

- Quick Play public matchmaking by entering only a player name
- Private room creation with custom settings
- Private room join by room code
- Automatic public match start when 2 players are matched
- Lobby with player list, ready state, and host-controlled private game start
- Turn-based drawing rounds
- Word selection for the active drawer
- Realtime canvas sync over WebSockets
- Guess submission and scoring
- Hint reveal over time
- Winner announcement at game end

## Backend setup

1. Open a terminal in the backend folder.
2. Run:

```powershell
cd E:\MCA\Web3Task_Assignment\skribbl-clone-backend
mvn "-Dmaven.repo.local=E:/MCA/Web3Task_Assignment/.m2repo" spring-boot:run
```

Or run the Spring Boot app directly from Eclipse.

The backend runs on `http://localhost:8080`.

## Frontend setup

Open this file in a browser after the backend is running:

- `E:\MCA\Web3Task_Assignment\skribbl-clone-frontend\index.html`

If your browser blocks local-file API/WebSocket access, serve the frontend folder with Live Server.

## How to test

### Public match

1. Open the frontend in browser A.
2. Enter a name.
3. Click `Enter Name & Join Room`.
4. Open the frontend in browser B.
5. Enter another name.
6. Click `Enter Name & Join Room`.
7. Once 2 players are matched, the public game starts automatically.

### Private room

1. Open the frontend in browser A.
2. Enter a name.
3. Choose private room settings.
4. Click `Create Private Room`.
5. Copy the room code shown in the UI.
6. Open the frontend in browser B.
7. Enter another name and paste the room code.
8. Click `Join by Code`.
9. In the private room, players can ready up and the host can start the game.

## Architecture overview

- REST endpoints bootstrap the session by creating, joining, or matchmaking into a room and return the player's `playerId` plus the current room state.
- STOMP channels handle live gameplay updates.
- `RoomService` holds in-memory active room state, public matchmaking, drawer rotation, scoring, hint timing, round progression, and the word bank.
- The frontend subscribes to room-wide topics for shared events and a player-specific topic for private drawer state such as word choices.

## Main endpoints and topics

### REST

- `POST /api/rooms`
- `POST /api/rooms/join`
- `POST /api/rooms/public`
- `GET /api/rooms/{roomId}?playerId=...`
- `GET /api/health`

### STOMP app destinations

- `/app/room.connect`
- `/app/room.ready`
- `/app/room.start`
- `/app/game.choose-word`
- `/app/game.draw`
- `/app/game.clear`
- `/app/game.undo`
- `/app/game.guess`

### STOMP subscriptions

- `/topic/rooms/{roomId}`
- `/topic/rooms/{roomId}/player/{playerId}`

## Render deployment

This project is prepared for a single-service Render deployment using Docker, with Spring Boot serving both the API and the frontend.

### Push to GitHub

Push the full project to a GitHub repository so Render can deploy it.

### Create a Render Web Service

Use these settings:

- Language: `Docker`
- Root Directory: `skribbl-clone-backend`

Leave the build and start command fields empty because Render will use the [Dockerfile](E:/MCA/Web3Task_Assignment/skribbl-clone-backend/Dockerfile).

### Environment

No database variables are required for the current in-memory version.

### After deploy

Render will provide a URL like:

- `https://your-service-name.onrender.com`

Open these paths to verify:

- `/`
- `/api/health`

The frontend uses same-origin requests in production, so REST and WebSocket traffic both work from the same Render domain.
