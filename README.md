# InvestLog

A simple logbook for investments — track wallets, stocks, funds and crypto holdings,
and see a consolidated overview across currencies.

InvestLog ships as three Docker containers orchestrated by a single Compose file:

- **client** — Vue 3 SPA, served by nginx (also reverse-proxies the API).
- **server** — Spring Boot 4 REST API (Kotlin / Java 25), built as a Cloud Native image.
- **postgres** — PostgreSQL 18 for persistence.

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

Log in with the seeded admin account: `admin@admin.com` / `admin` (from
`ADMIN_DEFAULT_PASSWORD`, see below) — **change this password immediately** in a real
deployment.

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
| `ADMIN_DEFAULT_PASSWORD` | `admin` | Password set on the seeded admin account on first boot |
| `GOOGLE_AUTH_ENABLED` | `false` | Enables Google OAuth2 login and shows the button client-side |
| `GOOGLE_CLIENT_ID` | _(empty)_ | From Google Cloud Console OAuth 2.0 Client ID |
| `GOOGLE_CLIENT_SECRET` | _(empty)_ | From Google Cloud Console |

### Google OAuth2 login (optional)

Local email/password login always works. To also enable "Continuar com Google":

1. In [Google Cloud Console](https://console.cloud.google.com/), create (or select) a project,
   then go to **APIs & Services → OAuth consent screen** and configure it (External user type is
   fine for a personal deployment; add your own Google account as a test user if the app stays in
   "Testing" publishing status).
2. Go to **APIs & Services → Credentials → Create Credentials → OAuth client ID**, application
   type **Web application**.
3. Under **Authorized redirect URIs**, add `http(s)://<your-host>/private/login/oauth2/code/google`
   (for a local run against the defaults in this README, that's
   `http://localhost:8081/private/login/oauth2/code/google`).
4. Copy the generated **Client ID** and **Client secret** into your `.env`:
   ```
   GOOGLE_AUTH_ENABLED=true
   GOOGLE_CLIENT_ID=<your client id>
   GOOGLE_CLIENT_SECRET=<your client secret>
   ```
5. Rebuild/restart the server so it picks up the new environment variables.

A user's first Google login creates a `PENDING` account, exactly like self-registration — an
admin still has to approve it in "Usuários locais" before that person can use anything else.

**Testing against `client/`'s dev server (`npm run dev`) instead of the docker-compose stack:**
the client and server run on separate origins in that setup (`localhost:5173` and `localhost:8080`),
so after the Google redirect the server can't just send the browser to a relative `/` — that would
land on the *server's* own port, not the client. `./gradlew bootRun` (no active Spring profile)
already defaults `CLIENT_BASE_URL` to `http://localhost:5173`, so no extra env var is needed —
just register `http://localhost:8080/private/login/oauth2/code/google` as the redirect URI in
Google Cloud Console for this case. The docker-compose stack runs with `SPRING_PROFILES_ACTIVE=prod`,
which overrides `CLIENT_BASE_URL` back to empty (client and server share one origin there, so a
relative redirect is already correct).

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
