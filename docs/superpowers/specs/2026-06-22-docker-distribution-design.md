# InvestLog — Docker Distribution Design

**Date:** 2026-06-22
**Status:** Approved (pending spec review)

## Goal

Distribute InvestLog (Vue SPA + Spring Boot API + Postgres) as Docker containers
run by a **single** root `compose.yaml`. Provide root-level scripts to **build** both
images, **back up** the database, and optionally **load** the demo dataset, plus a
polished root `README.md` with local run steps.

## Context (as-built)

- **server/** — Spring Boot 4.1 (Kotlin / Java 25) REST API under `/private/v1/*`
  (`WebMvcConfig` adds the `/private/{version}` prefix to every `@RestController`).
  Persistence is Postgres via jOOQ; schema is Liquibase (`db.changelog-master.xml`).
  - `prod` profile (`application-prod.yaml`) reads the datasource from env:
    `DB_HOST`, `DB_PORT` (default 5432), `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
  - Liquibase runs on every boot. With **no** `context`/`labels` gating, the
    `14-1050-seed-dev-data.xml` changeset seeds the fixed dev user
    (`google_sub = 'dev-user'`) and base currency rates — including under `prod`.
  - Auth is a fixed seeded dev user (`FixedCurrentUserProvider`); no login flow.
  - Mail starter is on the classpath but **not configured/wired** — no SMTP env needed.
  - Actuator is present. NOTE: the buildpack run image has no `curl`/`wget`, so no
    container HTTP healthcheck is used for the server.
  - Image is produced with Cloud Native Buildpacks: `./gradlew -p server bootBuildImage`
    → `investlog/server:v0.1.0` (name derived from Gradle `group`/`name`/`version`).
- **client/** — Vue 3 SPA built with Vite. API base is the relative path
  `/private/v1` (`apiClient` in `client/src/api`). In dev, Vite proxies `/private`
  → `http://localhost:8080`. In production the built static files must be served and
  `/private` reverse-proxied to the server. Relative base ⇒ no CORS concerns.
- **db** — `postgres:18-alpine`. The v18 image sets `PGDATA=/var/lib/postgresql/18/docker`.
  (The dev-only `server/compose.yaml` mounts its volume at the wrong path
  `/var/lib/postgresql/docker` and therefore does not actually persist data — a latent
  bug we deliberately do not replicate.)

## Decisions

| Decision | Choice |
| --- | --- |
| Orchestration | A **single** root `compose.yaml` runs postgres + server + client |
| Frontend serving | Separate **nginx** container serving the SPA and reverse-proxying `/private` → server |
| Build model | A root **build script** builds both images up front; compose references **prebuilt images** (no `build:`/`--build`) |
| Server image | `./gradlew -p server bootBuildImage` (Cloud Native Buildpacks) → `investlog/server:v0.1.0` |
| Client image | `docker build` of `client/Dockerfile` (multi-stage node → nginx) → `investlog/client:v0.1.0` |
| Database backup | Root **backup script**: `pg_dump` inside the container → file copied out to host `backups/` |
| Sample data | Root **helper scripts** that pipe `sample-data.sql` into `psql`, guarded by a dev-user check |
| Host port | nginx published on host **8081** → `http://localhost:8081` |
| Script location | All operational scripts (`build`, `backup`, `load-sample-data`) and the `README.md` live at the **repo root** |

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

### Service: `postgres`

- Image `postgres:18-alpine`.
- Env: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (from `.env`, defaults
  `investlog` / `sa_investlog` / `sa_investlog`, matching the project's dev convention).
- Named volume `postgres-data:/var/lib/postgresql/18/docker` (the real `PGDATA`).
- Healthcheck: `pg_isready -U <user> -d <db>`.
- Not published to host by default (internal only); optional `5432:5432` documented.

### Service: `server`

- Image `investlog/server:${SERVER_IMAGE_TAG:-v0.1.0}` (prebuilt by the build script).
- Env: `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST=postgres`, `DB_PORT=5432`, `DB_NAME`,
  `DB_USER`, `DB_PASSWORD` (mirroring the Postgres values).
- `depends_on: postgres: condition: service_healthy`.
- No host port published by default (nginx proxies it); optional `8080:8080` documented.
- No container healthcheck (buildpack image lacks curl/wget); ordering relies on the
  Postgres healthcheck plus Spring's own startup/migration. README points at
  `/actuator/health` for manual checks.

### Service: `client`

- Image `investlog/client:${CLIENT_IMAGE_TAG:-v0.1.0}` (prebuilt by the build script
  from `client/Dockerfile`).
- `client/Dockerfile` is multi-stage:
  1. `node:22-alpine` → `npm ci && npm run build` → `/app/dist`.
  2. `nginx:alpine` ← copies `dist` to `/usr/share/nginx/html`, copies `nginx.conf`.
- `client/nginx.conf`:
  - `location / { try_files $uri $uri/ /index.html; }` (SPA history fallback).
  - `location /private { proxy_pass http://server:8080; }` (no trailing slash, so the
    full `/private/...` path is preserved) plus standard proxy headers.
- Port mapping `${WEB_PORT:-8081}:80`.
- `depends_on: server`.

### Networking & volumes

- Single default bridge network; services reach each other by name (`postgres`, `server`).
- One named volume `postgres-data`.

## Configuration (`.env` / `.env.example`)

```
# Postgres
POSTGRES_DB=investlog
POSTGRES_USER=sa_investlog
POSTGRES_PASSWORD=sa_investlog

# Server (mirror the Postgres values)
DB_NAME=investlog
DB_USER=sa_investlog
DB_PASSWORD=sa_investlog

# Image tags
SERVER_IMAGE_TAG=v0.1.0
CLIENT_IMAGE_TAG=v0.1.0

# Host port for the web app
WEB_PORT=8081
```

`.env.example` is committed; `.env` is git-ignored. Defaults match the example so a
zero-config local run works.

## Scripts (repo root)

Each script ships in two flavours — `*.sh` (bash) and `*.ps1` (PowerShell, the user's
primary shell on Windows). All resolve config from `.env` with fallback to defaults.

### `build` (`build.sh` / `build.ps1`)

1. Build the server image: `./gradlew -p server bootBuildImage` → `investlog/server:v0.1.0`.
2. Build the client image: `docker build -t investlog/client:${CLIENT_IMAGE_TAG} ./client`.
3. Print the resulting image names and the next step (`docker compose up -d`).

### `backup` (`backup.sh` / `backup.ps1`)

1. Build a timestamped filename: `investlog-backup-YYYYMMDD-HHMMSS.sql`.
2. Run `pg_dump` **inside** the running Postgres container, writing to a file there:
   `docker compose exec -T postgres pg_dump -U <user> -d <db> -f /tmp/<file>`.
3. **Download** it to the host: `docker compose cp postgres:/tmp/<file> ./backups/<file>`.
4. Remove the in-container temp file; print the host path of the saved backup.
5. Create `backups/` on the host if missing (git-ignored).

### `load-sample-data` (`load-sample-data.sh` / `load-sample-data.ps1`)

1. Resolve DB name/user from `.env` (fallback to defaults).
2. **Guard:** query `system.users` for `google_sub = 'dev-user'`. If absent, print a
   clear message ("Start the stack and let the server finish migrating before seeding")
   and exit non-zero — avoids spewing FK errors.
3. Pipe `server/src/main/resources/sample-data.sql` into
   `docker compose exec -T postgres psql -U <user> -d <db>`.
4. Note in output that the script is **not idempotent** (re-running duplicates data).

## Files to create / change

| Path | Action |
| --- | --- |
| `compose.yaml` (root) | **new** — single-file orchestration of 3 prebuilt-image services, `.env`-driven |
| `client/Dockerfile` | **new** — multi-stage node → nginx |
| `client/nginx.conf` | **new** — SPA fallback + `/private` reverse proxy |
| `client/.dockerignore` | **new** — exclude `node_modules`, `dist` |
| `.env.example` | **new** — documented defaults |
| `.gitignore` | **update** — ignore `.env` and `backups/` (create if missing) |
| `build.sh` / `build.ps1` | **new** (root) — build both images |
| `backup.sh` / `backup.ps1` | **new** (root) — dump DB inside container, copy to host |
| `load-sample-data.sh` / `load-sample-data.ps1` | **new** (root) — load demo data |
| `README.md` (root) | **rewrite** — overview, architecture, prerequisites, local run steps, config table, backup, sample data, troubleshooting |

Out of scope / untouched: `server/compose.yaml` (dev-only Postgres for Spring Boot's
docker-compose support) stays as-is; the README distinguishes it from the root compose.
Its latent wrong-volume-path bug is noted but not fixed here.

## README outline (root)

1. What InvestLog is (one paragraph) + screenshot placeholder.
2. Architecture diagram.
3. Prerequisites (Docker + Compose v2; JDK 25 via the Gradle wrapper to build the
   server image).
4. **Run locally:**
   ```
   cp .env.example .env            # optional; defaults work out of the box
   ./build.sh                      # build server (gradle) + client (docker) images
   docker compose up -d            # start all three services
   ```
   App at `http://localhost:8081`.
5. Loading sample data (`./load-sample-data.sh` after the stack is healthy).
6. Backing up the database (`./backup.sh` → `backups/investlog-backup-<ts>.sql`).
7. Configuration table (env vars, defaults, meaning).
8. Stopping / resetting (`docker compose down`; `down -v` wipes the DB).
9. Local development (Vite dev server + Gradle `bootRun` + dev `server/compose.yaml`),
   distinct from the distribution compose.
10. Troubleshooting (port conflicts, rebuilding after a version bump, `/actuator/health`,
    "no dev user yet" when seeding too early).

## Verification

- `docker compose config` parses without error.
- `./build.sh` produces `investlog/server:v0.1.0` and `investlog/client:v0.1.0`.
- `docker compose up -d` brings all three services up; `http://localhost:8081` serves
  the SPA and the app loads data through `/private/v1/*`.
- `docker compose down && docker compose up -d` preserves DB data (correct volume path).
- `./backup.sh` writes a non-empty `.sql` file to host `backups/` and removes the temp
  file inside the container.
- `./load-sample-data.sh` populates wallets/holdings visible in the UI; run before the
  server has migrated, it exits cleanly with the guard message.
