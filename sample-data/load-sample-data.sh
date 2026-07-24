#!/usr/bin/env bash
# Load the demo dataset into the running InvestLog database.
#
# Prerequisites: the stack must be up and the server must have finished its Liquibase
# migrations, which seed the local admin account (admin@admin.com). This script is NOT
# idempotent — running it again duplicates the sample wallets/holdings.
#
# Usage: ./load-sample-data.sh [--container NAME] [--db NAME] [--user NAME]
#   --container (default investlog-postgres): the running Postgres container name —
#     matches what both compose.yaml and build-from-source/compose.yaml already name it.
#   --db (default investlog): the database to load the sample data into.
#   --user (default sa_investlog): the Postgres user to connect as.
#   All flags are optional and order-independent; override just what you need.
set -euo pipefail

cd "$(dirname "$0")"

CONTAINER="investlog-postgres"
DB_NAME="investlog"
DB_USER="sa_investlog"

while [ $# -gt 0 ]; do
    case "$1" in
        --container) CONTAINER="$2"; shift 2 ;;
        --db) DB_NAME="$2"; shift 2 ;;
        --user) DB_USER="$2"; shift 2 ;;
        *) echo "ERROR: unknown argument: $1" >&2; exit 1 ;;
    esac
done

SQL_FILE="sample-data.sql"

if [ ! -f "${SQL_FILE}" ]; then
    echo "ERROR: ${SQL_FILE} not found." >&2
    exit 1
fi

# Guard: the admin account must exist (seeded by Liquibase once the server has migrated).
echo "==> Checking that the server has migrated (admin account present)..."
has_user="$(docker exec "${CONTAINER}" \
    psql -U "${DB_USER}" -d "${DB_NAME}" -tAc \
    "SELECT 1 FROM system.users WHERE email = 'admin@admin.com'" 2>/dev/null || true)"

if [ "${has_user}" != "1" ]; then
    echo "ERROR: admin@admin.com not found. Start the stack and let the server finish" >&2
    echo "       migrating before seeding (docker compose up -d, then wait a bit)." >&2
    exit 1
fi

echo "==> Loading sample data (this is NOT idempotent)..."
docker exec -i "${CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" < "${SQL_FILE}"

echo
echo "Sample data loaded."
