#!/usr/bin/env bash
# Back up the InvestLog database.
# Runs pg_dump INSIDE the postgres container, then downloads the .sql file to the host.
#
# Usage: ./backup.sh
# Output: backups/investlog-backup-YYYYMMDD-HHMMSS.sql
set -euo pipefail

cd "$(dirname "$0")"

# On Git Bash / MSYS (Windows), absolute "/tmp/..." arguments are auto-rewritten to a
# Windows host path before reaching the container. Disable that conversion so the
# in-container paths below are passed through verbatim. Harmless on Linux/macOS.
export MSYS_NO_PATHCONV=1

if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
fi

DB_NAME="${DB_NAME:-${POSTGRES_DB:-investlog}}"
DB_USER="${DB_USER:-${POSTGRES_USER:-sa_investlog}}"

timestamp="$(date +%Y%m%d-%H%M%S)"
filename="investlog-backup-${timestamp}.sql"
container_path="/tmp/${filename}"

mkdir -p backups

echo "==> Dumping database '${DB_NAME}' inside the container..."
docker compose exec -T postgres pg_dump -U "${DB_USER}" -d "${DB_NAME}" -f "${container_path}"

echo "==> Downloading backup to host..."
docker compose cp "postgres:${container_path}" "backups/${filename}"

echo "==> Cleaning up the temp file inside the container..."
docker compose exec -T postgres rm -f "${container_path}"

echo
echo "Backup saved to: backups/${filename}"
