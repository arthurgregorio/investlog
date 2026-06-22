# InvestLog — Docker Distribution Design

**Date:** 2026-06-22
**Status:** Approved (pending spec review)

## Goal

Distribute InvestLog (Vue SPA + Spring Boot API + Postgres) as a set of Docker
containers orchestrated by a single root `compose.yaml`, with a polished root
`README.md` documenting how to build and run the whole application. Provide an
**opt-in** way to load the demo dataset from `server/src/main/resources/sample-data.sql`.

## Context (as-built)

- **server/** — Spring Boot 4.1 (Kotlin / Java 25) REST API. Endpoints are served
  under `/private/v1/*` (`WebMvcConfig` adds the `/private/{version}` prefix to all
  `@RestController`s). Persistence is Postgres via jOOQ; schema is managed by
  Liquibase (`db.changelog-master.xml`).
  - `prod` profile (`application-prod.yaml`) reads the datasource from env:
    `DB_HOST`, `DB_PORT` (default 5432), `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
  - Liquibase runs on every boot and, with **no** `context`/`labels` gating, the
    `14-1050-seed-dev-data.xml` changeset seeds the fixed dev user
    (`google_sub = 'dev-user'`) and base currency rates — including under `prod`.
  - Auth is a fixed seeded dev user (`FixedCurrentUserProvider`); no login flow.
  - Mail starter is on the classpath but **not configured/wired** — no SMTP env needed.
  - Actuator is present. NOTE: the buildpack run image has no `curl`/`wget`, so a
    container HTTP healthcheck is not used for the server.
  - Image is produced with Cloud Native Buildpacks: `./gradlew -p server bootBuildImage`
    → `investlog/server:v0.1.0` (name derived from Gradle `group`/`name`/`version`).
- **client/** — Vue 3 SPA built with Vite. API base is the relative path
  `/private/v1` (`apiClient` in `client/src/api`). In dev, Vite proxies `/private`
  → `http://localhost:8080`. In production the built static files must be served
  and `/private` reverse-proxied to the server. Relative base ⇒ no CORS concerns.
- **db** — `postgres:18-alpine`. The v18 image sets `PGDATA=/var/lib/postgresql/18/docker`.
  (The existing dev-only `server/compose.yaml` mounts its volume at the wrong path
  `/var/lib/postgresql/docker` and therefore does not actually persist data — a
  latent bug we deliberately do not replicate.)

## Decisions

| Decision | Choice |
| --- | --- |
| Frontend serving | Separate **nginx** container serving the SPA and reverse-proxying `/private` → server |
| Server image build | Two-step: `bootBuildImage` (buildpacks) first, then `docker compose up` references the prebuilt image |
| Client image build | Multi-stage `client/Dockerfile` (node build → nginx), built by compose |
| Sample data | **Helper scripts** (`.sh` + `.ps1`) that pipe the SQL into `psql` in the running Postgres container, guarded by a dev-user existence check |
| Host port | nginx published on host **8081** → `http://localhost:8081` |

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
- Env: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (from `.env`,
  defaults `investlog` / `sa_investlog` / `sa_investlog`, matching the project's
  existing dev convention).
- Named volume `postgres-data:/var/lib/postgresql/18/docker` (real `PGDATA`).
- Healthcheck: `pg_isready -U <user> -d <db>`.
- Not published to host by default (internal only); optional `5432:5432` documented.

### Service: `server`

- Image `investlog/server:${SERVER_IMAGE_TAG:-v0.1.0}` (prebuilt via `bootBuildImage`).
- Env: `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST=postgres`, `DB_PORT=5432`,
  `DB_NAME`, `DB_USER`, `DB_PASSWORD` (mirroring the Postgres values).
- `depends_on: postgres: condition: service_healthy`.
- No host port published by default (nginx proxies it); optional `8080:8080` documented.
- No container healthcheck (buildpack image lacks curl/wget); ordering relies on
  Postgres healthcheck + Spring's own startup/migration. README points at
  `/actuator/health` for manual checks.

### Service: `client`

- Built from `client/Dockerfile` (multi-stage):
  1. `node:22-alpine` → `npm ci && npm run build` → `/app/dist`.
  2. `nginx:alpine` ← copies `dist` to `/usr/share/nginx/html`, copies `nginx.conf`.
- `nginx.conf`:
  - `location / { try_files $uri $uri/ /index.html; }` (SPA history fallback).
  - `location /private { proxy_pass http://server:8080; }` (no trailing slash, so the
    full `/private/...` path is preserved) plus standard proxy headers.
- Port mapping `8081:80`.
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

# Server image tag (matches Gradle project version)
SERVER_IMAGE_TAG=v0.1.0

# Host port for the web app
WEB_PORT=8081
```

`.env.example` is committed; `.env` is git-ignored (real values, with safe defaults
identical to the example for a zero-config local run).

## Sample data loading

Two scripts under `scripts/`:

- `scripts/load-sample-data.sh` (bash)
- `scripts/load-sample-data.ps1` (PowerShell — primary on the user's Windows box)

Behavior (both):
1. Resolve DB name/user from `.env` (fallback to defaults).
2. **Guard:** query `system.users` for `google_sub = 'dev-user'`. If absent, print a
   clear message ("Start the stack and let the server finish migrating before
   seeding") and exit non-zero — avoids spewing FK errors.
3. Pipe `server/src/main/resources/sample-data.sql` into
   `docker compose exec -T postgres psql -U <user> -d <db>`.
4. Note in output that the script is **not idempotent** (re-running duplicates data).

## Files to create / change

| Path | Action |
| --- | --- |
| `compose.yaml` (root) | **new** — 3-service orchestration, `.env`-driven |
| `client/Dockerfile` | **new** — multi-stage node → nginx |
| `client/nginx.conf` | **new** — SPA fallback + `/private` reverse proxy |
| `client/.dockerignore` | **new** — exclude `node_modules`, `dist` |
| `.env.example` | **new** — documented defaults |
| `.gitignore` | **update** — ignore `.env` (add if missing) |
| `scripts/load-sample-data.sh` | **new** |
| `scripts/load-sample-data.ps1` | **new** |
| `README.md` (root) | **rewrite** — overview, architecture, prerequisites, quick start, config table, dev vs. prod, ports, sample data, troubleshooting |

Out of scope / untouched: `server/compose.yaml` (dev-only Postgres for Spring Boot's
docker-compose support) stays as-is; the README will distinguish it from the root
compose. The latent wrong-volume-path bug in it is noted but not fixed here.

## README outline

1. What InvestLog is (one paragraph) + screenshot placeholder.
2. Architecture diagram (the ASCII above or a cleaner rendering).
3. Prerequisites (Docker + Compose v2; JDK 25 / Gradle wrapper only for building the
   server image).
4. Quick start:
   ```
   ./gradlew -p server bootBuildImage      # build server image (buildpacks)
   cp .env.example .env                     # optional; defaults work out of the box
   docker compose up -d --build             # build client + start everything
   ```
   App at `http://localhost:8081`.
5. Loading sample data (run the helper script after the stack is healthy).
6. Configuration table (env vars, defaults, meaning).
7. Stopping / resetting (`docker compose down`, `down -v` to wipe the DB).
8. Local development (the existing `client` Vite dev server + `server` Gradle run +
   dev `server/compose.yaml`), distinct from the distribution compose.
9. Troubleshooting (port conflicts, rebuilding after server version bump, checking
   `/actuator/health`, "no dev user yet" when seeding too early).

## Verification

- `docker compose config` parses without error.
- `./gradlew -p server bootBuildImage` produces `investlog/server:v0.1.0`.
- `docker compose up -d --build` brings all three services up; `http://localhost:8081`
  serves the SPA and the app loads real (empty) data through `/private/v1/*`.
- `docker compose down && docker compose up -d` preserves DB data (correct volume path).
- Running the sample-data script populates wallets/holdings visible in the UI; running
  it before the server has migrated exits cleanly with the guard message.
