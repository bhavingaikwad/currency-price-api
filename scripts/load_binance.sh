#!/usr/bin/env bash
# Loads every downloaded Binance 1-minute kline CSV into `rates` using Postgres COPY.
# Stores the CLOSE price of each minute as the rate of <BASE>/USDT, timestamped at the candle's open.
# Spot timestamps from 2025-01-01 on are in MICROseconds.
. "$(dirname "$0")/common.sh"

DIR="$ROOT/data/binance"
loaded=0
skipped=0

for csv in "$DIR"/*-1m-*.csv; do
  [ -f "$csv" ] || { echo "no csv files in $DIR"; exit 1; }
  name="$(basename "$csv")"
  symbol="${name%%-*}"
  base="${symbol%USDT}"

  already=$($PSQL -At -c "SELECT count(*) FROM data_loads WHERE dataset = 'binance_1m' AND file_name = '$name'")
  if [ "$already" != "0" ]; then
    skipped=$((skipped + 1))
    continue
  fi

  {
    echo "BEGIN;"
    echo "CREATE TEMP TABLE stg (open_time bigint, open numeric, high numeric, low numeric, close numeric, volume numeric, close_time bigint, quote_volume numeric, trades bigint, taker_base numeric, taker_quote numeric, ignored text) ON COMMIT DROP;"
    echo "COPY stg FROM STDIN WITH (FORMAT csv);"
    cat "$csv"
    printf '\\.\n'
    echo "WITH ins AS (INSERT INTO rates (source, target, rate_time, value, provider) SELECT '$base', 'USDT', TIMESTAMPTZ 'epoch' + open_time * INTERVAL '1 microsecond', close, 'BINANCE' FROM stg RETURNING 1) INSERT INTO data_loads (dataset, file_name, rows_loaded) SELECT 'binance_1m', '$name', count(*) FROM ins;"
    echo "COMMIT;"
  } | $PSQL

  loaded=$((loaded + 1))
  echo "loaded $name"
done

echo "files loaded: $loaded  skipped (already loaded): $skipped"
$PSQL -c "ANALYZE rates"
