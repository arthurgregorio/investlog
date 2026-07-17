#!/usr/bin/env bash
# Back up the InvestLog database.
# Runs pg_dump INSIDE the postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.sh [--full|--data-only] [path/to/compose.yaml]
#   --data-only (default): dumps table data only. The common case for routine backups,
#     since Liquibase already owns schema creation/migration on next boot. A data-only
#     dump restores into an already-migrated (schema-present) database.
#   --full: dumps schema + data, for complete disaster-recovery-style backups.
#   Compose file defaults to the root compose.yaml (quick-start stack). Pass
#   build-from-source/compose.yaml to target that stack instead.
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
set -euo pipefail

cd "$(dirname "$0")"

MODE="--data-only"
COMPOSE_FILE="compose.yaml"

for arg in "$@"; do
    case "${arg}" in
        --full) MODE="--full" ;;
        --data-only) MODE="--data-only" ;;
        *) COMPOSE_FILE="${arg}" ;;
    esac
done

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

PG_DUMP_ARGS=()
if [ "${MODE}" = "--data-only" ]; then
    PG_DUMP_ARGS+=(--data-only)
fi

echo "==> Dumping database '${DB_NAME}' inside the container (${COMPOSE_FILE}, ${MODE})..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres pg_dump -U "${DB_USER}" -d "${DB_NAME}" "${PG_DUMP_ARGS[@]}" -f "${container_path}"

echo "==> Downloading backup to host..."
docker compose -f "${COMPOSE_FILE}" cp "postgres:${container_path}" "backups/${filename}"

echo "==> Cleaning up the temp file inside the container..."
docker compose -f "${COMPOSE_FILE}" exec -T postgres rm -f "${container_path}"

echo
echo "Backup saved to: backups/${filename}"
