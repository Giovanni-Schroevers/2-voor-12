# 2-voor-12

Backend for a digital version of the Dutch quiz game **2 voor 12 (Twee voor twaalf)**.

## Stack

- **Ktor** (JVM / Netty) — HTTP server
- **Exposed** (R2DBC) — async database access
- **PostgreSQL** — relational database, run via Docker Compose
- **Koog** + **Google Gemini** — AI-assisted question and word generation
- **JWT** — stateless authentication (admin + user roles)

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **21** | Any distribution (e.g. Temurin, GraalVM) |
| Docker | 24+ | Required for PostgreSQL and the optional Adminer UI |

The Gradle wrapper (`./gradlew`) downloads Gradle automatically — no separate installation needed.

---

## Quick start (development)

Make sure you have **JDK 21** and **Docker** installed, then:

```bash
# 1. Clone and enter the project
git clone <repo-url>
cd 2-voor-12

# 2. Create your local env file and add your secrets
cp .env.example .env
#    Set at least GOOGLE_API_KEY and ADMIN_PASSWORD in .env

# 3. Start PostgreSQL
docker compose up -d db

# 4. Load env vars and launch the server
set -a; source .env; set +a
./gradlew run
```

The server is now running at **http://localhost:8080**.

> Tip: use `./gradlew run --continuous` for automatic rebuilds on file changes.

---

## Environment variables

Copy `.env.example` to `.env` and set the values below. The file is gitignored — never commit real secrets.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOOGLE_API_KEY` | ✅ | — | Gemini API key — get a free one at https://aistudio.google.com/apikey |
| `ADMIN_PASSWORD` | ✅ | — | Shared password for `POST /api/admin/auth` |
| `JWT_SECRET` | | `secret` | HMAC-256 secret used to sign JWTs. Change this in any non-local environment. |
| `UPLOAD_DIR` | | `uploads` | Directory for uploaded avatar images. Mount a volume here in Docker so images survive restarts. |
| `DB_HOST` | | `localhost` | PostgreSQL host |
| `DB_PORT` | | `5432` | PostgreSQL port |
| `DB_NAME` | | `app` | Database name |
| `DB_USER` | | `app` | Database user |
| `DB_PASSWORD` | | `app` | Database password |

The defaults for all `DB_*` variables match `docker-compose.yml`, so no changes are needed when using the bundled Compose setup.

---

## Database

Start just the database (and the optional Adminer UI):

```bash
# PostgreSQL only
docker compose up -d db

# PostgreSQL + Adminer (web-based DB browser at http://localhost:8081)
docker compose up -d
```

The schema is created automatically on first startup — Exposed runs the migrations when the application boots.

To stop and remove containers (data is preserved in the `db_data` Docker volume):

```bash
docker compose down
```

To wipe the database volume entirely:

```bash
docker compose down -v
```

---

## Running the server

Load the `.env` file into your shell and start the server with the Gradle wrapper:

```bash
set -a; source .env; set +a
./gradlew run
```

The server binds to **http://0.0.0.0:8080** by default.

### Hot reload (continuous development)

Run with `--continuous` to rebuild and restart automatically whenever a source file changes:

```bash
set -a; source .env; set +a
./gradlew run --continuous
```

---

## Running tests

```bash
set -a; source .env; set +a
./gradlew test
```

Test results are written to `build/reports/tests/test/index.html`.

---

## Building a production JAR

```bash
./gradlew buildFatJar
```

The self-contained JAR is written to `build/libs/`. Run it with:

```bash
java -jar build/libs/2-voor-12-all.jar
```

Remember to set the environment variables (or pass them as `-D` JVM properties) before running the JAR.

---

## API overview

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/` | — | Health check |
| `POST` | `/api/admin/auth` | — | Exchange `{ "password": "..." }` for an admin JWT |
| `POST` | `/api/user/register` | — | Register a new user account |
| `POST` | `/api/user/login` | — | Log in and receive a user JWT |
| `PUT` | `/api/user` | user JWT | Update username / email |
| `DELETE` | `/api/user` | user JWT | Delete account |
| `POST` | `/api/user/avatar` | user JWT | Upload avatar image |
| `GET` | `/api/questions` | admin JWT | List all questions |
| `POST` | `/api/questions` | admin JWT | Create a question |
| `PUT` | `/api/questions/{id}` | admin JWT | Update a question |
| `DELETE` | `/api/questions/{id}` | admin JWT | Delete a question |
| `POST` | `/api/questions/draft` | admin JWT | AI-generate a question draft |
| `GET` | `/api/game/solo` | user JWT | Generate a solo round |
| `WS` | `/api/gamesocket` | user JWT | Multiplayer game WebSocket |

---

## Project structure

```
src/main/kotlin/
├── model/                      # Domain models, enums, and DTOs
│   ├── Question.kt             #   Question, PaardensprongPuzzle, TaartpuzzelPuzzle
│   ├── SoloGame.kt             #   SoloRound, RoundInventory, PuzzlePreference
│   ├── GameResult.kt           #   GameResult, PlayerResult, GameOutcome
│   ├── ClientMessage.kt        #   Sealed WebSocket messages (client → server)
│   ├── ServerMessage.kt        #   Sealed WebSocket messages (server → client)
│   ├── PlayerProfile.kt        #   Public player identity for lobbies
│   ├── User.kt                 #   User + registration / login / update DTOs
│   ├── QuestionRecord.kt       #   Persisted question with database id
│   └── QuestionSummary.kt      #   Aggregate stats over the question bank
├── repository/                 # Data access layer
│   ├── UserRepository.kt       #   Interface
│   ├── QuestionRepository.kt   #   Interface
│   ├── ExposedUserRepository.kt
│   └── ExposedQuestionRepository.kt
├── service/                    # Business logic
│   ├── UserService.kt
│   ├── QuestionService.kt
│   ├── SoloGameService.kt      #   Round generation (RoundGenerator interface)
│   ├── WordService.kt          #   LLM-backed word generation (WordGenerator interface)
│   ├── QuestionAssistantService.kt  # LLM-backed question drafting
│   ├── QuestionDraft.kt        #   LLM output schema for question drafts
│   └── GeneratedWord.kt        #   LLM output schema for word generation
├── game/                       # Multiplayer game state
│   ├── LobbyManager.kt         #   Player, Lobby, LobbyManager, JoinResult
│   └── GameSession.kt          #   In-progress game between two players
├── routing/                    # Ktor route definitions
│   ├── User.kt
│   ├── Admin.kt
│   ├── Questions.kt
│   ├── Game.kt
│   ├── GameSocket.kt
│   └── Multipart.kt
├── App.kt                      # Ktor application and module wiring
├── main.kt                     # Entry point
├── Exposed.kt                  # Database connection + schema setup
├── Security.kt                 # JWT configuration (JwtConfig)
├── AvatarStorage.kt            # Avatar file management
├── Passwords.kt                # Bcrypt hashing helpers
├── Http.kt                     # HTTP plugin configuration
├── Serialization.kt            # kotlinx.serialization setup
└── Websockets.kt               # WebSocket plugin configuration
```
