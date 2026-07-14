# InvestLog — CI/CD Pipeline Design

**Date:** 2026-07-12
**Status:** Approved (pending spec review)

## Goal

Add GitHub Actions automation so that:

1. Client changes trigger frontend tests; server changes trigger backend tests.
2. Those tests run on every PR and on every merge to `main`.
3. Publishing a GitHub release builds and pushes the server and client Docker images to
   Docker Hub.
4. Saving a **draft** release does not publish any image.

Alongside this, restructure local Docker Compose usage into two scopes (run published
images vs. build from source) and bring `README.md` up to date (undocumented env var,
the client dev-server port change, and how to opt into unreleased `main` changes).

## Context (as-built)

- `.github/workflows/codeql.yml` already has the pattern this design reuses: a `changes`
  job using `dorny/paths-filter@v4` to detect `server/**` vs `client/**` changes, gating
  downstream jobs with `needs.changes.outputs.<layer> == 'true'`.
- **Server** tests: `./gradlew test` (JVM 25, Liberica). Requires Docker on the runner —
  `compileKotlin` depends on `jooqCodegen`, which spins up a throwaway Testcontainers
  Postgres to generate jOOQ sources; `ubuntu-latest` has Docker preinstalled, so no extra
  setup is needed (see `server/CLAUDE.md`).
- **Client** tests: `npm run lint`, `npm run type-check`, `npm run test` (Node 22, per
  `client/CLAUDE.md` and `client/Dockerfile`).
- **Server image**: not a Dockerfile — built via Spring Boot's Cloud Native Buildpacks
  Gradle plugin, `./gradlew bootBuildImage --imageName=<name>`, same mechanism
  `build.sh`/`build.ps1` already use locally (see
  `docs/superpowers/specs/2026-06-22-docker-distribution-design.md`).
- **Client image**: `docker build ./client` (multi-stage Node → nginx Dockerfile).
- Docker Hub repos already created by the user: `arthurgregorio/investlog-server` and
  `arthurgregorio/investlog-client` (public, separate repos — not a shared namespace).
- Existing local build tags (`investlog/server`, `investlog/client`, per `compose.yaml`
  and `build.sh`) are a **separate, untouched** naming scheme for local-build artifacts
  and are not reconciled with the published Docker Hub names.
- Repo: `arthurgregorio/investlog`. GitHub release tags follow `vX.Y.Z[-rcN]` (e.g.
  `v0.1.0-rc1`, currently the only tag/release, marked pre-release).
- `client/vite.config.ts` was recently changed (commit `337c08a`) to fix the Vite dev
  server to port `8081` (`strictPort: true`), previously the Vite default `5173`. The
  server's default `CLIENT_BASE_URL` (`application.yaml`, non-prod profile) still says
  `http://localhost:5173` — a known stale default, tracked as a separate server-layer
  fix (out of scope here; flagged as a standalone follow-up task, not part of this
  design).
- `application-prod.yaml` exposes `CLIENT_BASE_URL` (default empty string, meaning
  "same-origin relative redirect") — a real, working env var that is currently
  undocumented in both `README.md`'s configuration table and `.env.example`.

## Decisions

| Decision | Choice |
| --- | --- |
| Test trigger | `pull_request` (any target branch) + `push` to `main` |
| Test scope gating | Reuse `codeql.yml`'s `changes` job (`dorny/paths-filter@v4`) |
| Server test command | `./gradlew test --no-daemon` |
| Client test command | `npm ci && npm run lint && npm run type-check && npm run test` |
| Release trigger | `release: types: [published]` — GitHub does not fire this for draft saves, so drafts are excluded without extra conditionals |
| Release test gate | None — release re-uses main's already-green commit, no re-test |
| Server publish | `./gradlew bootBuildImage --imageName=arthurgregorio/investlog-server:<tag>`, then `docker tag ... :latest`, then push both tags |
| Client publish | `docker/build-push-action@v6` against `client/Dockerfile`, pushing `<tag>` and `latest` in one step |
| Image tag source | `github.event.release.tag_name` (e.g. `v1.2.3`), plus a floating `latest` |
| Docker Hub auth | `docker/login-action@v3` using repo secrets `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` (user adds these manually — not handled by this design) |
| Root Compose scope | Pulls published images unconditionally: `arthurgregorio/investlog-server:latest`, `arthurgregorio/investlog-client:latest` — no version pinning, no build step |
| Build-from-source scope | Relocated to `build-from-source/` — today's `compose.yaml` (local `investlog/server`/`investlog/client` tags, `${SERVER_IMAGE_TAG:-v0.1.0}` etc.) plus `build.sh`/`build.ps1`, paths adjusted for the new nesting |
| Operational scripts | `backup.sh`/`backup.ps1`/`load-sample-data.sh`/`.ps1` stay at repo root only; gain an optional compose-file argument (default root `compose.yaml`) so they can target either stack |
| README | Documents both run modes, adds `CLIENT_BASE_URL` to the config table, updates the Vite dev port references (`8081`, not `5173`) and OAuth dev-testing instructions, tells users to use `build-from-source/` for unreleased `main` changes |
| Issue/PR split | 2 issues, each root-level config (no `server/`/`client/` touched) — no layer split needed |

## Architecture — workflows

```
.github/workflows/
  codeql.yml     (existing, untouched)
  ci.yml         (new) — test gate
  release.yml    (new) — Docker Hub publish
```

### `ci.yml`

```
on:
  pull_request:
  push:
    branches: [main]

jobs:
  changes:            # dorny/paths-filter, same shape as codeql.yml
  test-server:         # needs: changes; if: changes.outputs.server == 'true'
  test-client:          # needs: changes; if: changes.outputs.client == 'true'
```

### `release.yml`

```
on:
  release:
    types: [published]

jobs:
  publish-server:   # gradlew bootBuildImage -> docker tag latest -> docker push (both tags)
  publish-client:   # docker/build-push-action@v6, tags: <tag_name>, latest
```

`publish-server` and `publish-client` run independently (no `needs:` between them) —
a failure in one does not block the other, and both are always attempted since a
release always ships both images together.

## Architecture — Compose scopes

```
compose.yaml                 # root, default: published images, no build
.env.example                 # root: DB/auth/port vars only (no image-tag vars)
build-from-source/
  compose.yaml                # today's setup: local investlog/server|client images
  build.sh / build.ps1         # relocated, paths adjusted (../server, ../client)
  .env.example                 # same vars as root .env.example + SERVER_IMAGE_TAG/CLIENT_IMAGE_TAG
backup.sh / backup.ps1         # stay at root; optional compose-file arg
load-sample-data.sh / .ps1     # stay at root; optional compose-file arg
```

Root `compose.yaml`'s `server`/`client` services reference
`arthurgregorio/investlog-server:latest` / `arthurgregorio/investlog-client:latest`
directly (no `${...}` interpolation for the tag) — running `docker compose up -d` at
the repo root with no prior build step is the documented quick-start path.

`build-from-source/compose.yaml` is otherwise an unmodified copy of today's root
`compose.yaml` (same service definitions, same `${SERVER_IMAGE_TAG:-v0.1.0}` /
`${CLIENT_IMAGE_TAG:-v0.1.0}` interpolation), relocated one directory deeper.

## README changes

- Restructure "Run locally" into two subsections:
  - **Quick start** — `docker compose up -d` at root, pulls `:latest` from Docker Hub,
    no build required. This is the primary/first-shown path.
  - **Build from source** — for running unreleased `main` changes:
    `cd build-from-source && ./build.sh && docker compose up -d`.
- Configuration table: add a `CLIENT_BASE_URL` row (default empty, "same-origin
  redirect; set explicitly if client/server are served from different origins").
  Remove `SERVER_IMAGE_TAG`/`CLIENT_IMAGE_TAG` from the root table; add a small
  secondary table under "Build from source" for those two.
- "Local development" section and the Google OAuth section: replace `localhost:5173`
  references with `localhost:8081` (the Vite dev server's now-fixed port), and change
  the OAuth dev-testing instructions to tell the reader to set
  `CLIENT_BASE_URL=http://localhost:8081` explicitly (not "no extra step needed" —
  that claim depends on a still-open server-side fix tracked separately).
- Troubleshooting: no change needed beyond what's covered above.

## Files to create / change

| Path | Action |
| --- | --- |
| `.github/workflows/ci.yml` | **new** |
| `.github/workflows/release.yml` | **new** |
| `compose.yaml` (root) | **rewrite** — published-image services, `:latest`, no build |
| `.env.example` (root) | **update** — drop image-tag vars, add `CLIENT_BASE_URL` |
| `build-from-source/compose.yaml` | **new** (moved from root, content unchanged) |
| `build-from-source/build.sh` / `build.ps1` | **new** (moved from root, paths adjusted) |
| `build-from-source/.env.example` | **new** |
| `build.sh` / `build.ps1` (root) | **delete** (moved) |
| `backup.sh` / `backup.ps1` | **update** — optional compose-file arg |
| `load-sample-data.sh` / `load-sample-data.ps1` | **update** — optional compose-file arg |
| `README.md` | **update** — two run modes, config table, port references |

Out of scope / untouched: `server/application.yaml`'s stale `CLIENT_BASE_URL` default
(`localhost:5173`) — tracked as a separate server-layer fix, not part of this design.
`server/compose.yaml` (dev-only Postgres for Spring Boot's docker-compose support).

## Verification

- `.github/workflows/ci.yml`: a PR touching only `client/**` runs `test-client` and
  skips `test-server` (and vice versa); a PR touching both runs both; `act` or a real
  PR confirms the `changes` job outputs are correctly consumed.
- `.github/workflows/release.yml`: publishing `v0.1.0-rc1`-style test release (or a
  real one) results in both `arthurgregorio/investlog-server` and
  `-client` gaining a matching version tag and an updated `latest`; saving a **draft**
  release triggers neither job.
- `docker compose up -d` at repo root (no `.env`, no build) pulls and starts all three
  services from Docker Hub; app is reachable at `http://localhost:8081`.
- `cd build-from-source && ./build.sh && docker compose up -d` still reproduces the
  previous local-build workflow end-to-end.
- `./backup.sh` and `./load-sample-data.sh` work unmodified (default arg) against the
  root stack, and work against the `build-from-source` stack when passed its compose
  file.
- README's config table and port references match the actual code (`CLIENT_BASE_URL`
  present; `8081` used consistently).
