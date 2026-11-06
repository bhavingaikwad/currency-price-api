#!/usr/bin/env bash
# Loads the ECB euro reference rate history (wide CSV: one column per currency) into `rates`
# using Postgres COPY, unpivoting it into one row per (date, currency).
. "$(dirname "$0")/common.sh"

CSV="$ROOT/data/ecb/eurofxref-hist.csv"
NAME="$(basename "$CSV")"
[ -f "$CSV" ] || { echo "missing $CSV (download eurofxref-hist.zip first)"; exit 1; }

already=$($PSQL -At -c "SELECT count(*) FROM data_loads WHERE dataset = 'ecb_history' AND file_name = '$NAME'")
if [ "$already" != "0" ]; then
  echo "ECB history already loaded, skipping"
  exit 0
fi

{
  echo "BEGIN;"
  echo "CREATE TEMP TABLE stg (line text) ON COMMIT DROP;"
  echo "COPY stg (line) FROM STDIN;"
  tr -d '\r' < "$CSV"
  printf '\\.\n'
  cat << 'SQL'
WITH hdr AS (
    SELECT string_to_array(line, ',') AS h FROM stg WHERE line LIKE 'Date,%'
),
ins AS (
    INSERT INTO rates (source, target, rate_time, value, provider)
    SELECT 'EUR',
           (hdr.h)[i],
           (f[1]::date)::timestamp AT TIME ZONE 'UTC',
           f[i]::numeric,
           'ECB'
    FROM stg s
    CROSS JOIN hdr
    CROSS JOIN LATERAL string_to_array(s.line, ',') AS f
    CROSS JOIN LATERAL generate_subscripts(hdr.h, 1) AS i
    WHERE s.line NOT LIKE 'Date,%'
      AND i > 1
      AND (hdr.h)[i] <> ''
      AND f[i] ~ '[0-9]'
    RETURNING 1
)
INSERT INTO data_loads (dataset, file_name, rows_loaded)
SELECT 'ecb_history', 'eurofxref-hist.csv', count(*) FROM ins;
SQL
  echo "COMMIT;"
} | $PSQL

echo "loaded ECB history"
$PSQL -c "ANALYZE rates"
$PSQL -At -c "SELECT 'rows loaded: ' || rows_loaded FROM data_loads WHERE dataset = 'ecb_history'"
