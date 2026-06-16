# 2-voor-12

Backend for a digital version of the Dutch quiz game **2 voor 12 (Twee voor twaalf)**.

## Stack

- **Ktor** (JVM / Netty) — HTTP server
- **Exposed** (R2DBC) — database access
- **PostgreSQL** — database, run via Docker Compose
- **Koog** + **Google Gemini** — AI agent integration
- **JWT** — admin authentication

## Prerequisites

- JDK 21
- Docker (for the PostgreSQL database)

## Configuration

Copy the example env file and fill in your values:

```bash
cp .env.example .env
```

| Variable                                    | Purpose                                              | Default     |
|---------------------------------------------|------------------------------------------------------|-------------|
| `GOOGLE_API_KEY`                            | Google Gemini API key (https://aistudio.google.com/apikey) | — (required) |
| `ADMIN_PASSWORD`                            | Shared password for `POST /api/admin/auth`           | — (required) |
| `JWT_SECRET`                                | Secret used to sign admin JWTs                        | `secret`    |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection             | matches `docker-compose.yml` |

## Running

Start the database:

```bash
docker compose up -d db
```

Load the environment and start the server:

```bash
set -a; source .env; set +a
./gradlew run
```

The server starts at http://0.0.0.0:8080. The optional Adminer DB UI (via Docker) is at http://localhost:8081.

## Endpoints

| Method | Path               | Description                                  |
|--------|--------------------|----------------------------------------------|
| GET    | `/`                | Health check                                 |
| POST   | `/api/admin/auth`  | Exchange `{ "password": "..." }` for a JWT   |

Management endpoints live under `/api/admin` and are protected by the JWT issued from `/api/admin/auth`.

## Project structure

```
src/main/kotlin/
├── model/          # Domain models and enums (e.g. Question)
├── repository/     # Data access (Exposed table definitions + CRUD)
├── Admin.kt        # /api/admin endpoints
├── Exposed.kt      # Database connection + schema registration
├── Security.kt     # JWT configuration
└── ...             # Other Ktor module configuration
```
