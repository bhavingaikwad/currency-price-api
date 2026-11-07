#!/usr/bin/env bash
# Runs the same k6 load against each latest-rates endpoint and prints the key numbers.
# Usage: scripts/loadtest.sh [provider] [vus] [duration]
set -euo pipefail

PROVIDER="${1:-BINANCE}"
VUS="${2:-10}"
DURATION="${3:-30s}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

PASSWORD="$(docker exec currency-app printenv READER_PASSWORD)"
TOKEN="$(curl -s -X POST "$BASE_URL/api/v1/auth/token" -H 'Content-Type: application/json' \
  -d "{\"username\":\"reader\",\"password\":\"$PASSWORD\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')"

for endpoint in latest-naive latest latest-cached; do
  echo "=== $endpoint ($PROVIDER, $VUS virtual users, $DURATION) ==="
  # one unmeasured request so every run starts warm
  curl -s -o /dev/null -m 120 -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/v1/rates/$endpoint?provider=$PROVIDER"
  k6 run --quiet -e BASE_URL="$BASE_URL" -e ENDPOINT="$endpoint" -e PROVIDER="$PROVIDER" \
    -e VUS="$VUS" -e DURATION="$DURATION" -e TOKEN="$TOKEN" loadtest/latest.js 2>&1 \
    | grep -E "http_req_duration|http_reqs|http_req_failed|checks_succeeded" || true
done
