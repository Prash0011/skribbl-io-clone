# Skribble.io Clone

A real-time multiplayer drawing and guessing game inspired by skribbl.io. Players can join public matchmaking or create private rooms, draw on a shared canvas, guess words in real time, and compete on points across multiple rounds.

## Live Demo
- Live URL: https://skribbl-io-clone-rbl2.onrender.com
- GitHub Repository: https://github.com/Prash0011/skribbl-io-clone

## Tech Stack
- Backend: Java 17, Spring Boot
- Real-time Communication: Spring WebSocket, STOMP, SockJS
- Frontend: HTML, CSS, JavaScript
- Rendering: HTML Canvas API
- Storage: In-memory room state and word list
- Deployment: Render using Docker

## Features Implemented
- Public matchmaking
- Private room creation
- Invite-based private room joining
- Real-time drawing synchronization
- Turn-based drawing and guessing rounds
- Word selection for the drawer
- Guess checking and score calculation
- Leaderboard and winner announcement
- Hint reveal over time
- Undo and clear canvas controls
- Player disconnect cleanup
- Public deployment

## Core Game Flow
1. A player enters a nickname and either joins public matchmaking or creates a private room.
2. In a private room, the host can invite other players using a shareable link.
3. Once enough players are present, the game starts.
4. One player becomes the drawer and chooses a word.
5. The drawer draws on the shared canvas while other players submit guesses.
6. Correct guesses earn points and rounds continue until all configured rounds are completed.
7. The player with the highest score wins.

## Local Setup

### Prerequisites
- Java 17+
- Maven

### Run Locally
```bash
cd skribbl-clone-backend
mvn spring-boot:run
Then open:

http://localhost:8080
Architecture Overview
High-Level Design
This project is designed as a real-time multiplayer game with a Spring Boot backend and a lightweight HTML/CSS/JavaScript frontend. The backend manages rooms, game state, players, scores, and synchronization. The frontend handles user interaction, canvas drawing, chat input, and live room updates.

Backend Responsibilities
The backend is responsible for:

creating and joining rooms
public matchmaking
private room invite handling
managing rounds and drawer rotation
handling word selection
scoring guesses
revealing hints
removing disconnected players
broadcasting game state updates
Main backend files:

RoomService.java handles room lifecycle, turn order, gameplay rules, scores, and public/private room behavior
RoomController.java exposes REST endpoints for room creation, joining, matchmaking, and room fetch
GameMessageController.java handles real-time STOMP messages
WebSocketConfig.java configures WebSocket/STOMP endpoints and broker setup
Frontend Responsibilities
The frontend is responsible for:

landing page and room entry flow
private room invite/join UI
canvas drawing interactions
chat and guess input
rendering room/player/game status
listening to real-time updates from the backend
Main frontend files:

index.html
styles.css
app.js
REST + WebSocket Communication
The application uses a hybrid communication model.

REST is used for:

room creation
room joining
public matchmaking
initial room state bootstrap
WebSocket/STOMP is used for:

live room state updates
drawing strokes
guesses
word selection
canvas clear/undo
system/game messages
Drawing Synchronization
The drawer uses the HTML canvas to draw strokes. Each stroke includes:

start and end coordinates
selected color
brush size
The frontend sends stroke data through WebSocket, and the backend broadcasts it to all players in the room. Every client re-renders the same stroke so all players stay visually in sync.

Game State Management
The backend keeps active game state in memory, including:

room ID and room type
players in the room
current host
current drawer
selected word
hint state
scores
round number
timer state
winner
Word Matching Logic
Guess validation normalizes text by:

trimming whitespace
converting to lowercase
collapsing repeated spaces
This improves matching reliability for user guesses.

Disconnect Handling
If a player closes the tab or disconnects:

they are removed from the room
their turn is removed from rotation
if they were the host, another player becomes host
if they were the current drawer, the game advances safely
empty public rooms are deleted
API / Messaging Overview
REST Endpoints
POST /api/rooms
POST /api/rooms/join
POST /api/rooms/public
GET /api/rooms/{roomId}?playerId=...
GET /api/health
STOMP App Destinations
/app/room.connect
/app/room.ready
/app/room.start
/app/game.choose-word
/app/game.draw
/app/game.clear
/app/game.undo
/app/game.guess
STOMP Subscriptions
/topic/rooms/{roomId}
/topic/rooms/{roomId}/player/{playerId}
Deployment
The application is deployed publicly on Render.

Deployment details:

Docker-based deployment
single service for backend + frontend
Spring Boot serves static frontend files and backend APIs from the same domain
WebSocket communication works on the deployed production URL
Live URL:

https://skribbl-io-clone-rbl2.onrender.com
Limitations
Current storage is in-memory only
game state is lost if the server restarts
no authentication or persistent user accounts
public matchmaking is simpler than the original skribbl.io
free hosting may take time to wake up after inactivity
Submission Checklist
This project includes the assignment deliverables:

working deployed application
source code repository
README with setup instructions
live deployment URL
architecture overview
explanation-ready code structure
