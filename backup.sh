#!/usr/bin/env bash
# Back up the InvestLog database.
# Runs pg_dump INSIDE the running postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.sh [--full|--data-only] [container-name]
#   --data-only (default): dumps table data only. The common case for routine backups,
#     since Liquibase already owns schema creation/migration on next boot. A data-only
#     dump restores into an already-migrated (schema-present) database.
#   --full: dumps schema + data, for complete disaster-recovery-style backups.
#   Container name defaults to investlog-postgres — the name both compose.yaml and
#   build-from-source/compose.yaml give the postgres container. Pass a different name if
#   you're running Postgres under another container name (or without Compose at all —
#   this only needs the container to be running, not a compose.yaml on disk).
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
set -euo pipefail

cd "$(dirname "$0")"

MODE="--data-only"
CONTAINER="investlog-postgres"

for arg in "$@"; do
    case "${arg}" in
        --full) MODE="--full" ;;
        --data-only) MODE="--data-only" ;;
        *) CONTAINER="${arg}" ;;
    esac
done

# On Git Bash / MSYS (Windows), absolute "/tmp/..." arguments are auto-rewritten to a
# Windows host path before reaching the container. Disable that conversion so the
# in-container paths below are passed through verbatim. Harmless on Linux/macOS.
export MSYS_NO_PATHCONV=1

DB_NAME="${DB_NAME:-investlog}"
DB_USER="${DB_USER:-sa_investlog}"

timestamp="$(date +%Y%m%d-%H%M%S)"
filename="investlog-backup-${timestamp}.sql"
container_path="/tmp/${filename}"

mkdir -p backups

PG_DUMP_ARGS=()
if [ "${MODE}" = "--data-only" ]; then
    PG_DUMP_ARGS+=(--data-only)
fi

echo "==> Dumping database '${DB_NAME}' inside container '${CONTAINER}' (${MODE})..."
docker exec "${CONTAINER}" pg_dump -U "${DB_USER}" -d "${DB_NAME}" "${PG_DUMP_ARGS[@]}" -f "${container_path}"

echo "==> Downloading backup to host..."
docker cp "${CONTAINER}:${container_path}" "backups/${filename}"

echo "==> Cleaning up the temp file inside the container..."
docker exec "${CONTAINER}" rm -f "${container_path}"

echo
echo "Backup saved to: backups/${filename}"
