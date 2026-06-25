#!/usr/bin/env bash
# Load the demo dataset into the running InvestLog database.
#
# Prerequisites: the stack must be up and the server must have finished its Liquibase
# migrations (which seed the 'dev-user'). This script is NOT idempotent — running it
# again duplicates the sample wallets/holdings.
#
# Usage: ./load-sample-data.sh
set -euo pipefail

cd "$(dirname "$0")"

if [ -f .env ]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env
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
has_user="$(docker compose exec -T postgres \
    psql -U "${DB_USER}" -d "${DB_NAME}" -tAc \
    "SELECT 1 FROM system.users WHERE google_sub = 'dev-user'" 2>/dev/null || true)"

if [ "${has_user}" != "1" ]; then
    echo "ERROR: dev-user not found. Start the stack and let the server finish" >&2
    echo "       migrating before seeding (docker compose up -d, then wait a bit)." >&2
    exit 1
fi

echo "==> Loading sample data (this is NOT idempotent)..."
docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" < "${SQL_FILE}"

echo
echo "Sample data loaded."
