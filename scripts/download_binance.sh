#!/usr/bin/env bash
# Downloads Binance spot 1-minute kline files, verifies each checksum, and unzips them.
# Already-downloaded files are skipped, so it is safe to re-run.
. "$(dirname "$0")/common.sh"

SYMBOLS="${SYMBOLS:-BTCUSDT ETHUSDT BNBUSDT SOLUSDT XRPUSDT ADAUSDT DOGEUSDT TRXUSDT AVAXUSDT LINKUSDT DOTUSDT LTCUSDT BCHUSDT ATOMUSDT UNIUSDT NEARUSDT XLMUSDT ETCUSDT FILUSDT AAVEUSDT}"
MONTHS="${MONTHS:-2026-01 2026-02 2026-03 2026-04 2026-05 2026-06}"
BASE_URL="https://data.binance.vision/data/spot/monthly/klines"
OUT="$ROOT/data/binance"
mkdir -p "$OUT"

missing=0
for sym in $SYMBOLS; do
  for month in $MONTHS; do
    name="${sym}-1m-${month}"
    [ -f "$OUT/$name.csv" ] && continue
    if ! curl -fsSL "$BASE_URL/$sym/1m/$name.zip" -o "$OUT/$name.zip" \
       || ! curl -fsSL "$BASE_URL/$sym/1m/$name.zip.CHECKSUM" -o "$OUT/$name.zip.CHECKSUM"; then
      echo "MISSING $name"
      rm -f "$OUT/$name.zip" "$OUT/$name.zip.CHECKSUM"
      missing=$((missing + 1))
      continue
    fi
    (cd "$OUT" && shasum -a 256 -c "$name.zip.CHECKSUM" > /dev/null)
    unzip -oq "$OUT/$name.zip" -d "$OUT"
    echo "ok $name"
  done
done

echo "csv files present: $(ls "$OUT"/*.csv 2>/dev/null | wc -l | tr -d ' ')  missing downloads: $missing"
