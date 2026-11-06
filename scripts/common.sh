#!/usr/bin/env bash
# Shared settings for the data scripts. Source this file; do not run it directly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Load POSTGRES_USER / POSTGRES_DB from .env if present (never committed).
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
fi

# Command that runs psql inside our Postgres container. Reads SQL from stdin.
PSQL="${PSQL:-docker exec -i currency-postgres psql -U ${POSTGRES_USER:-currency} -d ${POSTGRES_DB:-currency} -v ON_ERROR_STOP=1 -q}"
