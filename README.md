# InvestLog

A simple logbook for investments — track wallets, stocks, funds and crypto holdings,
and see a consolidated overview across currencies.

InvestLog ships as three Docker containers orchestrated by a single Compose file:

- **client** — Vue 3 SPA, served by nginx (also reverse-proxies the API).
- **server** — Spring Boot 4 REST API (Kotlin / Java 25), built as a Cloud Native image.
- **postgres** — PostgreSQL 18 for persistence.

> _Screenshot placeholder — add a screenshot of the dashboard here._

## Architecture

```
┌─────────┐   /          ┌──────────────┐   /private    ┌──────────────┐
│ browser │ ───────────► │ client       │ ────────────► │ server       │
│         │  :8081       │ nginx:alpine │   proxy_pass  │ Spring  :8080│
└─────────┘              │ serves SPA   │               └──────┬───────┘
                         └──────────────┘                      │ JDBC
                                                        ┌──────▼───────┐
                                                        │ postgres:18  │
                                                        └──────────────┘
```

The browser only talks to nginx on port `8081`. nginx serves the SPA and forwards
every `/private/*` request to the server container, so there are no cross-origin calls.

## Prerequisites

- **Docker** with **Docker Compose v2** (`docker compose ...`).
- A **JDK 25** is *not* required on the host — the Gradle wrapper downloads its own
  toolchain. You only need Docker to build the server image (Buildpacks run in Docker).

## Run locally

```bash
# 1. (optional) create your .env — the committed defaults already work
cp .env.example .env

# 2. build both images (server via Gradle Buildpacks, client via docker build)
./build.sh           # Windows PowerShell: ./build.ps1

# 3. start the whole stack
docker compose up -d
```

Open **http://localhost:8081**.

On first start the server runs its database migrations and seeds the default user, so
the app is immediately usable (with no data yet). Use the script below to add demo data.

## Load sample data (optional)

Populate the database with demo wallets, stocks, funds and crypto holdings:

```bash
./load-sample-data.sh        # Windows PowerShell: ./load-sample-data.ps1
```

Run this **after** the stack is up and the server has finished migrating. The script
checks for the seeded user first and refuses to run otherwise. It is **not idempotent**
— running it twice duplicates the sample data.

## Back up the database

Dump the database to a timestamped `.sql` file on the host:

```bash
./backup.sh                  # Windows PowerShell: ./backup.ps1
```

`pg_dump` runs inside the postgres container; the resulting file is then copied out to
`backups/investlog-backup-YYYYMMDD-HHMMSS.sql` (the `backups/` folder is git-ignored).

## Configuration

All settings are read from `.env` (copy from `.env.example`). The defaults work out of
the box for a local run.

| Variable | Default | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | `investlog` | Database name |
| `POSTGRES_USER` | `sa_investlog` | Database user |
| `POSTGRES_PASSWORD` | `sa_investlog` | Database password |
| `DB_NAME` | `investlog` | DB name the server connects to (mirror of `POSTGRES_DB`) |
| `DB_USER` | `sa_investlog` | DB user the server connects with |
| `DB_PASSWORD` | `sa_investlog` | DB password the server connects with |
| `SERVER_IMAGE_TAG` | `v0.1.0` | Tag for the `investlog/server` image |
| `CLIENT_IMAGE_TAG` | `v0.1.0` | Tag for the `investlog/client` image |
| `WEB_PORT` | `8081` | Host port the web app is published on |

## Stop / reset

```bash
docker compose down        # stop and remove containers (keeps the database volume)
docker compose down -v     # also delete the database volume (wipes all data)
```

## Health & logs

```bash
docker compose ps                       # service status
docker compose logs -f server           # follow server logs
```

The server exposes Spring Boot Actuator; once it is up, its health endpoint is
reachable inside the network at `http://server:8080/actuator/health`.

## Local development (without Docker images)

The Compose setup above is for running the packaged app. For day-to-day development:

- **Database:** the dev-only `server/compose.yaml` is started automatically by Spring
  Boot's Docker Compose support when you run the server from your IDE / Gradle.
- **Server:** `cd server && ./gradlew bootRun`.
- **Client:** `cd client && npm install && npm run dev` — Vite serves on its own port
  and proxies `/private` to `http://localhost:8080`.

## Troubleshooting

- **Port 8081 already in use** — set a different `WEB_PORT` in `.env` and re-run
  `docker compose up -d`.
- **`load-sample-data` says the dev-user is missing** — the server hasn't finished
  migrating yet. Wait a few seconds (`docker compose logs -f server`) and retry.
- **Changed the project version** — bump `SERVER_IMAGE_TAG` / `CLIENT_IMAGE_TAG` in
  `.env`, re-run `./build.sh`, then `docker compose up -d`.
- **Data disappeared after `down`** — make sure you didn't run `down -v`; that flag
  deletes the `postgres-data` volume.
