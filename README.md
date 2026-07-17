# InvestLog

A simple logbook for investments — track wallets, stocks, funds and crypto holdings,
and see a consolidated overview across currencies.

InvestLog ships as three Docker containers orchestrated by Compose:

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
- That's it for the quick start below — no JDK, no Node, nothing else to install.
  Building from source (see below) additionally uses Docker to run the
  Buildpacks-based server build; a JDK is still *not* required on the host, since the
  Gradle wrapper downloads its own toolchain.

## Run locally

InvestLog has two ways to run: **quick start**, using published images from Docker Hub
(fastest, always the latest tagged release), and **build from source**, for running
whatever is currently on `main` before it's released.

### Quick start (recommended)

```bash
# 1. (optional) create your .env — the committed defaults already work
cp .env.example .env

# 2. start the whole stack — pulls arthurgregorio/investlog-server:latest and
#    arthurgregorio/investlog-client:latest from Docker Hub, no build needed
docker compose up -d
```

Open **http://localhost:8081**.

### Build from source

Use this to run changes from `main` that haven't been released yet.

```bash
cd build-from-source

# 1. (optional) create your .env — the committed defaults already work
cp .env.example .env

# 2. build both images (server via Gradle Buildpacks, client via docker build)
./build.sh           # Windows PowerShell: ./build.ps1

# 3. start the whole stack
docker compose up -d
```

Open **http://localhost:8081**.

---

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

Targets the quick-start stack (root `compose.yaml`) by default. If you're running the
build-from-source stack instead, pass its compose file explicitly:

```bash
./load-sample-data.sh build-from-source/compose.yaml
```

## Back up the database

Dump the database to a timestamped `.sql` file on the host:

```bash
./backup.sh                  # Windows PowerShell: ./backup.ps1
```

`pg_dump` runs inside the postgres container; the resulting file is then copied out to
`backups/investlog-backup-YYYYMMDD-HHMMSS.sql` (the `backups/` folder is git-ignored).

Defaults to a data-only dump — the common case for routine backups, since Liquibase already
owns schema creation/migration on next boot. Pass `--full` for a complete schema + data
dump instead, for disaster-recovery-style backups:

```bash
./backup.sh --full                  # Windows PowerShell: ./backup.ps1 --full
./backup.sh --data-only              # explicit, same as the default
```

There's no restore script today — a data-only dump restores into an already-migrated
(schema-present) database.

Like `load-sample-data.sh`, this targets the quick-start stack by default — pass
`build-from-source/compose.yaml` as an argument (alongside the mode flag, in either order)
to target that stack instead.

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
| `WEB_PORT` | `8081` | Host port the web app is published on |
| `ADMIN_DEFAULT_PASSWORD` | `admin` | Password set on the seeded admin account on first boot |
| `GOOGLE_AUTH_ENABLED` | `false` | Enables Google OAuth2 login and shows the button client-side |
| `GOOGLE_CLIENT_ID` | _(empty)_ | From Google Cloud Console OAuth 2.0 Client ID |
| `GOOGLE_CLIENT_SECRET` | _(empty)_ | From Google Cloud Console |
| `CLIENT_BASE_URL` | _(empty)_ | Redirect target after Google login. Leave empty for a same-origin deployment (the default stack serves client and server behind one nginx origin, so a relative redirect is already correct) — only set this if client and server are served from different origins |

Building from source adds two more variables, read from `build-from-source/.env`:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_IMAGE_TAG` | `v0.1.0` | Tag for the locally-built `investlog/server` image |
| `CLIENT_IMAGE_TAG` | `v0.1.0` | Tag for the locally-built `investlog/client` image |

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
the client and server run on separate origins in that setup (`localhost:8081` and `localhost:8080`),
so after the Google redirect the server can't just send the browser to a relative `/` — that would
land on the *server's* own port, not the client. `./gradlew bootRun` (no active Spring profile)
already defaults `CLIENT_BASE_URL` to `http://localhost:8081`, so no extra env var is needed —
just register `http://localhost:8080/private/login/oauth2/code/google` as the redirect URI in
Google Cloud Console for this case. The docker-compose stack runs with `SPRING_PROFILES_ACTIVE=prod`,
which overrides `CLIENT_BASE_URL` back to empty (client and server share one origin there, so a
relative redirect is already correct).

## Stop / reset

```bash
docker compose down        # stop and remove containers (keeps the database volume)
docker compose down -v     # also delete the database volume (wipes all data)
```

Run from `build-from-source/` instead if that's the stack you started.

## Health & logs

```bash
docker compose ps                       # service status
docker compose logs -f server           # follow server logs
```

The server exposes Spring Boot Actuator; once it is up, its health endpoint is
reachable inside the network at `http://server:8080/actuator/health`.

## Local development (without Docker images)

The Compose setups above are for running the packaged app. For day-to-day development:

- **Database:** the dev-only `server/compose.yaml` is started automatically by Spring
  Boot's Docker Compose support when you run the server from your IDE / Gradle.
- **Server:** `cd server && ./gradlew bootRun`.
- **Client:** `cd client && npm install && npm run dev` — Vite serves on
  **http://localhost:8081** and proxies `/private` to `http://localhost:8080`.

## Troubleshooting

- **Port 8081 already in use** — set a different `WEB_PORT` in `.env` and re-run
  `docker compose up -d`. Note the Vite dev server (`npm run dev`) also defaults to
  `8081`, so don't run it and the Docker stack at the same time without changing one of
  them.
- **`load-sample-data` says the dev-user is missing** — the server hasn't finished
  migrating yet. Wait a few seconds (`docker compose logs -f server`) and retry.
- **Want the latest unreleased changes** — the quick-start stack always runs the last
  published release. Use `build-from-source/` instead (see "Build from source" above)
  to run whatever is currently on `main`.
- **Changed the project version (build from source)** — bump `SERVER_IMAGE_TAG` /
  `CLIENT_IMAGE_TAG` in `build-from-source/.env`, re-run `./build-from-source/build.sh`,
  then `docker compose up -d` from `build-from-source/`.
- **Data disappeared after `down`** — make sure you didn't run `down -v`; that flag
  deletes the `postgres-data` volume.
