# CI/CD Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GitHub Actions workflows that test client/server changes on every PR and
merge to `main`, and publish Docker images to Docker Hub on release (never on drafts);
restructure local Compose usage into a no-build "quick start" (published images) and a
"build from source" scope; bring `README.md`/`.env.example` up to date.

**Architecture:** Two new workflow files (`ci.yml`, `release.yml`) reusing the
`dorny/paths-filter` changed-layer pattern already in `codeql.yml`. Root `compose.yaml`
becomes the published-image quick start; today's build-from-source setup relocates to
`build-from-source/`. `backup.sh`/`load-sample-data.sh` (and `.ps1`) gain an optional
compose-file argument so one pair of scripts serves both scopes.

**Tech Stack:** GitHub Actions, Docker Compose, Gradle (Cloud Native Buildpacks), `gh` CLI.

## Global Constraints

- Docker Hub images (exact names, already created by the user): `arthurgregorio/investlog-server`, `arthurgregorio/investlog-client`.
- Release publish trigger: `release: types: [published]` — this does not fire for draft saves, satisfying "no image on draft" with no extra conditional logic.
- Root `compose.yaml` always uses the `latest` tag, hardcoded — no version-pinning env vars at root.
- Reuse the `changes` job pattern (`dorny/paths-filter@v4`, same filter shape) from `.github/workflows/codeql.yml` for both new workflows' path-based gating.
- `actions/checkout@v7`, `actions/setup-java@v5` (`distribution: liberica`, `java-version: "25"`) — match `codeql.yml`'s existing versions.
- Per root `CLAUDE.md`: every PR gets the `feature` label and is assigned to `arthurgregorio`; since neither PR touches `server/` or `client/`, each PR closes its own single tracking issue (`Closes #N`), no layer split needed.
- Repo: `arthurgregorio/investlog`.

---

### Task 1: Create GitHub tracking issues

**Files:** none (GitHub state only)

**Interfaces:**
- Produces: two issue numbers, referred to as `<ISSUE_CI>` and `<ISSUE_RELEASE>` in Tasks 3 and 9.

- [ ] **Step 1: Create the CI test workflow issue**

```bash
gh issue create \
  --title "ci: run frontend and backend tests on every PR and merge to main" \
  --body "Add a GitHub Actions workflow that runs backend tests (./gradlew test) when server/** changes and frontend tests (lint + type-check + vitest) when client/** changes, gated by changed-path detection (same dorny/paths-filter pattern as codeql.yml). Runs on every pull_request and on push to main.

See docs/superpowers/specs/2026-07-12-cicd-pipeline-design.md for the full design." \
  --label feature \
  --assignee arthurgregorio
```

Note the printed issue number — this is `<ISSUE_CI>`.

- [ ] **Step 2: Create the Docker release workflow issue**

```bash
gh issue create \
  --title "ci: publish Docker images on release; quick-start vs build-from-source Compose" \
  --body "Add a GitHub Actions workflow that builds and pushes arthurgregorio/investlog-server and arthurgregorio/investlog-client to Docker Hub when a (non-draft) release is published, tagged with the release tag and latest. Also restructure local Compose usage: root compose.yaml becomes a no-build quick start pulling the published :latest images; today's build-from-source setup (build.sh/build.ps1 + compose.yaml with locally-built images) moves to build-from-source/. Update README.md and .env.example to match, including the previously-undocumented CLIENT_BASE_URL env var and the client dev server's port change (5173 -> 8081).

See docs/superpowers/specs/2026-07-12-cicd-pipeline-design.md for the full design." \
  --label feature \
  --assignee arthurgregorio
```

Note the printed issue number — this is `<ISSUE_RELEASE>`.

- [ ] **Step 3: Verify both issues exist**

Run: `gh issue list --limit 5`
Expected: both new issues appear, `OPEN`, labeled `feature`, assigned to `arthurgregorio`.

---

### Task 2: `ci.yml` — test workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: workflow `CI` with jobs `changes`, `test-server`, `test-client`.

- [ ] **Step 1: Create the branch**

```bash
git checkout main
git pull
git checkout -b ci/add-test-workflow
```

- [ ] **Step 2: Write `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [ "main" ]

jobs:
  changes:
    name: Detect changed layers
    runs-on: ubuntu-latest
    outputs:
      server: ${{ steps.filter.outputs.server }}
      client: ${{ steps.filter.outputs.client }}
    steps:
      - uses: actions/checkout@v7
      - uses: dorny/paths-filter@v4
        id: filter
        with:
          filters: |
            server:
              - 'server/**'
            client:
              - 'client/**'

  test-server:
    name: Test server
    needs: changes
    if: needs.changes.outputs.server == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v5
        with:
          distribution: "liberica"
          java-version: "25"

      - name: Run server tests
        working-directory: server
        run: |
          ./gradlew test --no-daemon \
            -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g" \
            -Dkotlin.compiler.execution.strategy=in-process

  test-client:
    name: Test client
    needs: changes
    if: needs.changes.outputs.client == 'true'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-node@v5
        with:
          node-version: "22"
          cache: "npm"
          cache-dependency-path: client/package-lock.json

      - name: Install dependencies
        working-directory: client
        run: npm ci

      - name: Lint
        working-directory: client
        run: npm run lint

      - name: Type-check
        working-directory: client
        run: npm run type-check

      - name: Test
        working-directory: client
        run: npm run test
```

- [ ] **Step 3: Validate YAML syntax**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))" `

(If `python`/`pyyaml` isn't available, use: `node -e "require('yaml').parse(require('fs').readFileSync('.github/workflows/ci.yml','utf8'))"` with the `yaml` npm package available under `client/node_modules`, or open the file and visually diff indentation against `codeql.yml`, which shares the same `changes` job shape.)

Expected: no error output (valid YAML).

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: run frontend/backend tests on PRs and pushes to main"
```

---

### Task 3: Push `ci.yml` branch and open its PR

**Files:** none (GitHub state only)

**Interfaces:**
- Consumes: `<ISSUE_CI>` from Task 1.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin ci/add-test-workflow
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create \
  --title "ci: run frontend/backend tests on PRs and pushes to main" \
  --body "Closes #<ISSUE_CI>

Adds .github/workflows/ci.yml: detects whether server/** or client/** changed (same dorny/paths-filter pattern as codeql.yml) and runs the matching test suite — ./gradlew test for server, npm run lint/type-check/test for client. Triggers on every pull_request and on push to main." \
  --label feature \
  --assignee arthurgregorio
```

Replace `<ISSUE_CI>` with the actual issue number from Task 1.

- [ ] **Step 3: Verify the workflow actually runs**

Run: `gh pr checks --watch` (or open the PR's "Checks" tab)
Expected: the `CI` workflow appears and its `changes` job runs (since this PR itself
touches `.github/workflows/`, neither `test-server` nor `test-client` will trigger —
that's expected; a follow-up PR touching `client/**` or `server/**` is what exercises
those jobs, which Task 9's PR will do implicitly by not touching either).

---

### Task 4: `release.yml` — Docker publish workflow

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Produces: workflow `Release` with jobs `publish-server`, `publish-client`.

- [ ] **Step 1: Create the branch**

```bash
git checkout main
git pull
git checkout -b ci/docker-release-and-compose-restructure
```

- [ ] **Step 2: Write `.github/workflows/release.yml`**

```yaml
name: Release

on:
  release:
    types: [published]

jobs:
  publish-server:
    name: Publish server image
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v5
        with:
          distribution: "liberica"
          java-version: "25"

      - name: Log in to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build image with Cloud Native Buildpacks
        working-directory: server
        run: |
          ./gradlew bootBuildImage --no-daemon \
            --imageName=arthurgregorio/investlog-server:${{ github.event.release.tag_name }}

      - name: Tag latest
        run: |
          docker tag arthurgregorio/investlog-server:${{ github.event.release.tag_name }} \
            arthurgregorio/investlog-server:latest

      - name: Push images
        run: |
          docker push arthurgregorio/investlog-server:${{ github.event.release.tag_name }}
          docker push arthurgregorio/investlog-server:latest

  publish-client:
    name: Publish client image
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7

      - uses: docker/setup-buildx-action@v3

      - name: Log in to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: client
          push: true
          tags: |
            arthurgregorio/investlog-client:${{ github.event.release.tag_name }}
            arthurgregorio/investlog-client:latest
```

- [ ] **Step 3: Validate YAML syntax**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/release.yml'))" `
Expected: no error output.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: publish Docker images to Docker Hub on release"
```

**Note for whoever runs this workflow for real:** `DOCKERHUB_USERNAME` and
`DOCKERHUB_TOKEN` must exist as repo secrets (Settings → Secrets and variables →
Actions) before the first release publish. This plan does not set them — entering
credentials is something the user must do themselves.

---

### Task 5: Root `compose.yaml` — no-build quick start

**Files:**
- Modify: `compose.yaml` (root)
- Modify: `.env.example` (root)

**Interfaces:**
- Consumes: none.
- Produces: root `compose.yaml` referencing `arthurgregorio/investlog-server:latest` / `arthurgregorio/investlog-client:latest`; root `.env.example` without `SERVER_IMAGE_TAG`/`CLIENT_IMAGE_TAG`, with `CLIENT_BASE_URL` added.

- [ ] **Step 1: Rewrite `compose.yaml`**

Replace the full file contents with:

```yaml
name: investlog

# Single-file orchestration for the full InvestLog stack, using the published Docker
# Hub images (no build step). For running unreleased `main` changes instead, see
# build-from-source/.
# The app is served at http://localhost:${WEB_PORT:-8081}

services:

  postgres:
    container_name: investlog-postgres
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-investlog}
      POSTGRES_USER: ${POSTGRES_USER:-sa_investlog}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-sa_investlog}
    volumes:
      - postgres-data:/var/lib/postgresql/18/docker
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-sa_investlog} -d ${POSTGRES_DB:-investlog}"]
      interval: 5s
      timeout: 5s
      retries: 10
    networks:
      - investlog

  server:
    container_name: investlog-server
    image: arthurgregorio/investlog-server:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: ${DB_NAME:-investlog}
      DB_USER: ${DB_USER:-sa_investlog}
      DB_PASSWORD: ${DB_PASSWORD:-sa_investlog}
      ADMIN_DEFAULT_PASSWORD: ${ADMIN_DEFAULT_PASSWORD:-admin}
      GOOGLE_AUTH_ENABLED: ${GOOGLE_AUTH_ENABLED:-false}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:-}
      CLIENT_BASE_URL: ${CLIENT_BASE_URL:-}
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - investlog

  client:
    container_name: investlog-client
    image: arthurgregorio/investlog-client:latest
    ports:
      - "${WEB_PORT:-8081}:80"
    depends_on:
      - server
    networks:
      - investlog

volumes:
  postgres-data:
    driver: local

networks:
  investlog:
    driver: bridge
```

This also fixes a pre-existing gap: `ADMIN_DEFAULT_PASSWORD`, `GOOGLE_AUTH_ENABLED`,
`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` were documented in `.env.example`/README
but never actually passed into the `server` container's environment. Fixing this here
since it's the same environment block being touched to add `CLIENT_BASE_URL`.

- [ ] **Step 2: Rewrite `.env.example`**

```
# Copy to .env and adjust if needed. The defaults below work out of the box.

# --- Postgres ---
POSTGRES_DB=investlog
POSTGRES_USER=sa_investlog
POSTGRES_PASSWORD=sa_investlog

# --- Server datasource (mirror the Postgres values above) ---
DB_NAME=investlog
DB_USER=sa_investlog
DB_PASSWORD=sa_investlog

# --- Host port for the web app (nginx) ---
WEB_PORT=8081

# --- Auth ---
ADMIN_DEFAULT_PASSWORD=admin
GOOGLE_AUTH_ENABLED=false
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# Redirect target after Google login. Leave empty for a same-origin deployment (the
# default stack serves client and server behind one nginx origin, so a relative
# redirect is already correct). Only set this if client and server are served from
# different origins.
CLIENT_BASE_URL=
```

- [ ] **Step 3: Validate the compose file parses**

Run: `docker compose config`
Expected: prints the resolved compose config with no error (image names resolve to
`arthurgregorio/investlog-server:latest` / `arthurgregorio/investlog-client:latest`).

- [ ] **Step 4: Commit**

```bash
git add compose.yaml .env.example
git commit -m "feat: root compose.yaml pulls published images (no build step)"
```

---

### Task 6: `build-from-source/` — relocate the build scope

**Files:**
- Create: `build-from-source/compose.yaml`
- Create: `build-from-source/build.sh`
- Create: `build-from-source/build.ps1`
- Create: `build-from-source/.env.example`
- Delete: `build.sh` (root, moved)
- Delete: `build.ps1` (root, moved)

**Interfaces:**
- Consumes: none.
- Produces: `build-from-source/compose.yaml` (local-build image names), `build-from-source/build.sh`/`.ps1` (paths adjusted one level up).

- [ ] **Step 1: Move the build scripts with history preserved**

```bash
mkdir -p build-from-source
git mv build.sh build-from-source/build.sh
git mv build.ps1 build-from-source/build.ps1
```

- [ ] **Step 2: Fix `build-from-source/build.sh`'s relative paths**

Replace the full file contents with:

```bash
#!/usr/bin/env bash
# Build both Docker images for InvestLog from source:
#   - server: Spring Boot Cloud Native Buildpacks image (via Gradle)
#   - client: Vue SPA served by nginx (via docker build)
#
# Usage: ./build-from-source/build.sh (or: cd build-from-source && ./build.sh)
set -euo pipefail

cd "$(dirname "$0")"

# Load .env if present (for image tags); fall back to defaults otherwise.
if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
fi

SERVER_IMAGE_TAG="${SERVER_IMAGE_TAG:-v0.1.0}"
CLIENT_IMAGE_TAG="${CLIENT_IMAGE_TAG:-v0.1.0}"

echo "==> Building server image (Gradle bootBuildImage) -> investlog/server:${SERVER_IMAGE_TAG}"
( cd ../server && ./gradlew bootBuildImage --imageName="investlog/server:${SERVER_IMAGE_TAG}" )

echo "==> Building client image (docker build) -> investlog/client:${CLIENT_IMAGE_TAG}"
docker build -t "investlog/client:${CLIENT_IMAGE_TAG}" ../client

echo
echo "Done. Images:"
echo "  investlog/server:${SERVER_IMAGE_TAG}"
echo "  investlog/client:${CLIENT_IMAGE_TAG}"
echo
echo "Next: docker compose up -d   (run from this build-from-source/ folder)"
```

- [ ] **Step 3: Fix `build-from-source/build.ps1`'s relative paths**

Replace the full file contents with:

```powershell
# Build both Docker images for InvestLog from source:
#   - server: Spring Boot Cloud Native Buildpacks image (via Gradle)
#   - client: Vue SPA served by nginx (via docker build)
#
# Usage: ./build-from-source/build.ps1 (or: cd build-from-source; ./build.ps1)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

# Load .env if present (for image tags); fall back to defaults otherwise.
$envVars = @{}
if (Test-Path .env) {
    Get-Content .env | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)\s*=\s*(.*)\s*$') {
            $envVars[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
}

$serverTag = if ($envVars.ContainsKey('SERVER_IMAGE_TAG')) { $envVars['SERVER_IMAGE_TAG'] } else { 'v0.1.0' }
$clientTag = if ($envVars.ContainsKey('CLIENT_IMAGE_TAG')) { $envVars['CLIENT_IMAGE_TAG'] } else { 'v0.1.0' }
$webPort   = if ($envVars.ContainsKey('WEB_PORT'))         { $envVars['WEB_PORT'] }         else { '8081' }

Write-Host "==> Building server image (Gradle bootBuildImage) -> investlog/server:$serverTag"
Push-Location ..\server
try {
    & .\gradlew.bat bootBuildImage "--imageName=investlog/server:$serverTag"
    if ($LASTEXITCODE -ne 0) { throw "gradle bootBuildImage failed" }
}
finally { Pop-Location }

Write-Host "==> Building client image (docker build) -> investlog/client:$clientTag"
docker build -t "investlog/client:$clientTag" ..\client
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

Write-Host ""
Write-Host "Done. Images:"
Write-Host "  investlog/server:$serverTag"
Write-Host "  investlog/client:$clientTag"
Write-Host ""
Write-Host "Next: docker compose up -d   (run from this build-from-source/ folder, then open http://localhost:$webPort)"
```

- [ ] **Step 4: Create `build-from-source/compose.yaml`**

```yaml
name: investlog

# Single-file orchestration for the full InvestLog stack, built from source.
# Build the images first with ./build.sh (or build.ps1), then: docker compose up -d
# The app is served at http://localhost:${WEB_PORT:-8081}
# For a no-build quick start using published images instead, see the root compose.yaml.

services:

  postgres:
    container_name: investlog-postgres
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-investlog}
      POSTGRES_USER: ${POSTGRES_USER:-sa_investlog}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-sa_investlog}
    volumes:
      - postgres-data:/var/lib/postgresql/18/docker
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-sa_investlog} -d ${POSTGRES_DB:-investlog}"]
      interval: 5s
      timeout: 5s
      retries: 10
    networks:
      - investlog

  server:
    container_name: investlog-server
    image: investlog/server:${SERVER_IMAGE_TAG:-v0.1.0}
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: postgres
      DB_PORT: "5432"
      DB_NAME: ${DB_NAME:-investlog}
      DB_USER: ${DB_USER:-sa_investlog}
      DB_PASSWORD: ${DB_PASSWORD:-sa_investlog}
      ADMIN_DEFAULT_PASSWORD: ${ADMIN_DEFAULT_PASSWORD:-admin}
      GOOGLE_AUTH_ENABLED: ${GOOGLE_AUTH_ENABLED:-false}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:-}
      CLIENT_BASE_URL: ${CLIENT_BASE_URL:-}
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - investlog

  client:
    container_name: investlog-client
    image: investlog/client:${CLIENT_IMAGE_TAG:-v0.1.0}
    ports:
      - "${WEB_PORT:-8081}:80"
    depends_on:
      - server
    networks:
      - investlog

volumes:
  postgres-data:
    driver: local

networks:
  investlog:
    driver: bridge
```

- [ ] **Step 5: Create `build-from-source/.env.example`**

```
# Copy to .env (in this folder) and adjust if needed. The defaults below work out of
# the box.

# --- Postgres ---
POSTGRES_DB=investlog
POSTGRES_USER=sa_investlog
POSTGRES_PASSWORD=sa_investlog

# --- Server datasource (mirror the Postgres values above) ---
DB_NAME=investlog
DB_USER=sa_investlog
DB_PASSWORD=sa_investlog

# --- Image tags (match the Gradle project version) ---
SERVER_IMAGE_TAG=v0.1.0
CLIENT_IMAGE_TAG=v0.1.0

# --- Host port for the web app (nginx) ---
WEB_PORT=8081

# --- Auth ---
ADMIN_DEFAULT_PASSWORD=admin
GOOGLE_AUTH_ENABLED=false
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# Redirect target after Google login. Leave empty for a same-origin deployment.
CLIENT_BASE_URL=
```

- [ ] **Step 6: Validate the relocated compose file parses**

Run: `cd build-from-source && docker compose config && cd ..`
Expected: prints the resolved compose config with no error (image names resolve to
`investlog/server:v0.1.0` / `investlog/client:v0.1.0`).

- [ ] **Step 7: Commit**

```bash
git add build-from-source/ build.sh build.ps1
git commit -m "feat: relocate build-from-source Compose scope"
```

---

### Task 7: `backup`/`load-sample-data` scripts — support both scopes

**Files:**
- Modify: `backup.sh`
- Modify: `backup.ps1`
- Modify: `load-sample-data.sh`
- Modify: `load-sample-data.ps1`

**Interfaces:**
- Consumes: none.
- Produces: all four scripts accept an optional first argument/parameter `ComposeFile`/positional `$1`, defaulting to root `compose.yaml`, reading `.env` from that file's own directory.

- [ ] **Step 1: Rewrite `backup.sh`**

```bash
#!/usr/bin/env bash
# Back up the InvestLog database.
# Runs pg_dump INSIDE the postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.sh [path/to/compose.yaml]
#   Defaults to the root compose.yaml (quick-start stack). Pass
#   build-from-source/compose.yaml to target that stack instead.
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="${1:-compose.yaml}"
COMPOSE_DIR="$(dirname "${COMPOSE_FILE}")"

# On Git Bash / MSYS (Windows), absolute "/tmp/..." arguments are auto-rewritten to a
# Windows host path before reaching the container. Disable that conversion so the
# in-container paths below are passed through verbatim. Harmless on Linux/macOS.
export MSYS_NO_PATHCONV=1

if [ -f "${COMPOSE_DIR}/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "${COMPOSE_DIR}/.env"
    set +a
fi

DB_NAME="${DB_NAME:-${POSTGRES_DB:-investlog}}"
DB_USER="${DB_USER:-${POSTGRES_USER:-sa_investlog}}"

timestamp="$(date +%Y%m%d-%H%M%S)"
filename="investlog-backup-${timestamp}.sql"
container_path="/tmp/${filename}"

mkdir -p backups

echo "==> Dumping database '${DB_NAME}' inside the container (${COMPOSE_FILE})..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres pg_dump -U "${DB_USER}" -d "${DB_NAME}" -f "${container_path}"

echo "==> Downloading backup to host..."
docker compose -f "${COMPOSE_FILE}" cp "postgres:${container_path}" "backups/${filename}"

echo "==> Cleaning up the temp file inside the container..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres rm -f "${container_path}"

echo
echo "Backup saved to: backups/${filename}"
```

- [ ] **Step 2: Rewrite `backup.ps1`**

```powershell
# Back up the InvestLog database.
# Runs pg_dump INSIDE the postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.ps1 [path/to/compose.yaml]
#   Defaults to the root compose.yaml (quick-start stack). Pass
#   build-from-source/compose.yaml to target that stack instead.
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
param(
    [string]$ComposeFile = "compose.yaml"
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$composeDir = Split-Path -Parent $ComposeFile
if ([string]::IsNullOrEmpty($composeDir)) { $composeDir = '.' }

$envVars = @{}
$envFile = Join-Path $composeDir '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)\s*=\s*(.*)\s*$') {
            $envVars[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
}

function Get-EnvOrDefault($key, $fallbackKey, $default) {
    if ($envVars.ContainsKey($key))         { return $envVars[$key] }
    if ($envVars.ContainsKey($fallbackKey)) { return $envVars[$fallbackKey] }
    return $default
}

$dbName = Get-EnvOrDefault 'DB_NAME' 'POSTGRES_DB'   'investlog'
$dbUser = Get-EnvOrDefault 'DB_USER' 'POSTGRES_USER' 'sa_investlog'

$timestamp     = Get-Date -Format 'yyyyMMdd-HHmmss'
$filename      = "investlog-backup-$timestamp.sql"
$containerPath = "/tmp/$filename"

New-Item -ItemType Directory -Force -Path backups | Out-Null

Write-Host "==> Dumping database '$dbName' inside the container ($ComposeFile)..."
docker compose -f $ComposeFile exec -T postgres pg_dump -U $dbUser -d $dbName -f $containerPath
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }

Write-Host "==> Downloading backup to host..."
docker compose -f $ComposeFile cp "postgres:$containerPath" "backups/$filename"
if ($LASTEXITCODE -ne 0) { throw "docker compose cp failed" }

Write-Host "==> Cleaning up the temp file inside the container..."
docker compose -f $ComposeFile exec -T postgres rm -f $containerPath | Out-Null

Write-Host ""
Write-Host "Backup saved to: backups/$filename"
```

- [ ] **Step 3: Rewrite `load-sample-data.sh`**

```bash
#!/usr/bin/env bash
# Load the demo dataset into the running InvestLog database.
#
# Prerequisites: the stack must be up and the server must have finished its Liquibase
# migrations (which seed the 'dev-user'). This script is NOT idempotent — running it
# again duplicates the sample wallets/holdings.
#
# Usage: ./load-sample-data.sh [path/to/compose.yaml]
#   Defaults to the root compose.yaml (quick-start stack). Pass
#   build-from-source/compose.yaml to target that stack instead.
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE_FILE="${1:-compose.yaml}"
COMPOSE_DIR="$(dirname "${COMPOSE_FILE}")"

if [ -f "${COMPOSE_DIR}/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "${COMPOSE_DIR}/.env"
    set +a
fi

DB_NAME="${DB_NAME:-${POSTGRES_DB:-investlog}}"
DB_USER="${DB_USER:-${POSTGRES_USER:-sa_investlog}}"
SQL_FILE="server/src/main/resources/sample-data.sql"

if [ ! -f "${SQL_FILE}" ]; then
    echo "ERROR: ${SQL_FILE} not found." >&2
    exit 1
fi

# Guard: the dev-user must exist (created by Liquibase once the server has migrated).
echo "==> Checking that the server has migrated (dev-user present)..."
has_user="$(docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U "${DB_USER}" -d "${DB_NAME}" -tAc \
    "SELECT 1 FROM system.users WHERE google_sub = 'dev-user'" 2>/dev/null || true)"

if [ "${has_user}" != "1" ]; then
    echo "ERROR: dev-user not found. Start the stack and let the server finish" >&2
    echo "       migrating before seeding (docker compose up -d, then wait a bit)." >&2
    exit 1
fi

echo "==> Loading sample data (this is NOT idempotent)..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" < "${SQL_FILE}"

echo
echo "Sample data loaded."
```

- [ ] **Step 4: Rewrite `load-sample-data.ps1`**

```powershell
# Load the demo dataset into the running InvestLog database.
#
# Prerequisites: the stack must be up and the server must have finished its Liquibase
# migrations (which seed the 'dev-user'). This script is NOT idempotent — running it
# again duplicates the sample wallets/holdings.
#
# Usage: ./load-sample-data.ps1 [path/to/compose.yaml]
#   Defaults to the root compose.yaml (quick-start stack). Pass
#   build-from-source/compose.yaml to target that stack instead.
param(
    [string]$ComposeFile = "compose.yaml"
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$composeDir = Split-Path -Parent $ComposeFile
if ([string]::IsNullOrEmpty($composeDir)) { $composeDir = '.' }

$envVars = @{}
$envFile = Join-Path $composeDir '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)\s*=\s*(.*)\s*$') {
            $envVars[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
}

function Get-EnvOrDefault($key, $fallbackKey, $default) {
    if ($envVars.ContainsKey($key))         { return $envVars[$key] }
    if ($envVars.ContainsKey($fallbackKey)) { return $envVars[$fallbackKey] }
    return $default
}

$dbName  = Get-EnvOrDefault 'DB_NAME' 'POSTGRES_DB'   'investlog'
$dbUser  = Get-EnvOrDefault 'DB_USER' 'POSTGRES_USER' 'sa_investlog'
$sqlFile = 'server/src/main/resources/sample-data.sql'

if (-not (Test-Path $sqlFile)) {
    Write-Error "$sqlFile not found."
    exit 1
}

# Guard: the dev-user must exist (created by Liquibase once the server has migrated).
Write-Host "==> Checking that the server has migrated (dev-user present)..."
$hasUser = (docker compose -f $ComposeFile exec -T postgres `
    psql -U $dbUser -d $dbName -tAc `
    "SELECT 1 FROM system.users WHERE google_sub = 'dev-user'" 2>$null | Out-String).Trim()

if ($hasUser -ne '1') {
    Write-Error "dev-user not found. Start the stack and let the server finish migrating before seeding (docker compose up -d, then wait a bit)."
    exit 1
}

Write-Host "==> Loading sample data (this is NOT idempotent)..."
Get-Content $sqlFile -Raw | docker compose -f $ComposeFile exec -T postgres psql -U $dbUser -d $dbName
if ($LASTEXITCODE -ne 0) { throw "psql load failed" }

Write-Host ""
Write-Host "Sample data loaded."
```

- [ ] **Step 5: Shellcheck the two bash scripts**

Run: `shellcheck backup.sh load-sample-data.sh`
Expected: no errors (warnings about the sourced `.env`, if any, already existed before
this change and are pre-existing/ignored via the `shellcheck disable=SC1091` comment).
If `shellcheck` isn't installed, run `bash -n backup.sh && bash -n load-sample-data.sh`
instead (syntax check only) — expected: no output, exit code 0.

- [ ] **Step 6: Commit**

```bash
git add backup.sh backup.ps1 load-sample-data.sh load-sample-data.ps1
git commit -m "feat: backup/load-sample-data scripts accept a compose file argument"
```

---

### Task 8: Update `README.md`

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: `CLIENT_BASE_URL` (Task 5), `build-from-source/` layout (Task 6), compose-file argument (Task 7).

- [ ] **Step 1: Replace the full `README.md` contents**

```markdown
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

Like `load-sample-data.sh`, this targets the quick-start stack by default — pass
`build-from-source/compose.yaml` as the first argument to target that stack instead.

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
land on the *server's* own port, not the client. Set `CLIENT_BASE_URL=http://localhost:8081` in the
environment `./gradlew bootRun` runs with, and register
`http://localhost:8080/private/login/oauth2/code/google` as the redirect URI in Google Cloud Console
for this case. The docker-compose stack runs with `SPRING_PROFILES_ACTIVE=prod`, where
`CLIENT_BASE_URL` defaults to empty (client and server share one origin there, so a relative
redirect is already correct).

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
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document quick start vs build-from-source, CLIENT_BASE_URL, 8081 dev port"
```

---

### Task 9: Push the release/compose branch and open its PR

**Files:** none (GitHub state only)

**Interfaces:**
- Consumes: `<ISSUE_RELEASE>` from Task 1.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin ci/docker-release-and-compose-restructure
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create \
  --title "ci: publish Docker images on release; quick-start vs build-from-source Compose" \
  --body "Closes #<ISSUE_RELEASE>

- .github/workflows/release.yml: on release: types: [published] (never fires for draft saves), builds and pushes arthurgregorio/investlog-server (Cloud Native Buildpacks) and arthurgregorio/investlog-client (docker/build-push-action), tagged with the release tag and latest.
- Root compose.yaml now pulls the published :latest images directly — no build step. Also wires ADMIN_DEFAULT_PASSWORD/GOOGLE_*/CLIENT_BASE_URL into the server container's environment, which were previously documented but never actually passed through.
- Today's local-build setup (build.sh/build.ps1 + compose.yaml with locally-built images) moves to build-from-source/, for running unreleased main changes.
- backup.sh/load-sample-data.sh (and .ps1) accept an optional compose-file argument so they work against either stack.
- README.md documents both run modes, adds the previously-undocumented CLIENT_BASE_URL, and fixes stale localhost:5173 references now that the Vite dev server runs on :8081." \
  --label feature \
  --assignee arthurgregorio
```

Replace `<ISSUE_RELEASE>` with the actual issue number from Task 1.

- [ ] **Step 3: Verify CI passes on this PR**

Run: `gh pr checks --watch`
Expected: the `CI` workflow's `changes` job runs; since this PR touches neither
`server/**` nor `client/**`, `test-server`/`test-client` are correctly skipped — confirms
the path-filter gating from Task 2 behaves as designed. The `CodeQL` workflow behaves
the same way for the same reason.

---

## Post-plan manual steps (not automatable, for the user)

1. Add repo secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` (Settings → Secrets and
   variables → Actions) before publishing a real release — `release.yml` will fail at
   the login step without them.
2. Review and merge both PRs.
3. To smoke-test `release.yml` end-to-end, publish a real (non-draft) GitHub release and
   confirm both `arthurgregorio/investlog-server` and `arthurgregorio/investlog-client`
   gain a matching version tag and an updated `latest` on Docker Hub; then confirm a
   **draft** release does not trigger the workflow at all (check the Actions tab).
