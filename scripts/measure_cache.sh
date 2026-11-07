#!/usr/bin/env bash
# Compares /latest (DB, batched) with /latest-cached (Redis) and proves the freshness check.
# The app must be running. Usage: ./scripts/measure_cache.sh
. "$(dirname "$0")/common.sh"
set +e
BASE="${BASE:-http://localhost:8080}"
PROVIDER="${PROVIDER:-BINANCE}"
N="${N:-100}"

count() { v=$(curl -s "$BASE/actuator/metrics/rates.cache?tag=result:$1" | grep -o '"value":[0-9.]*' | head -1 | cut -d: -f2); echo "${v:-0}"; }
lat() { for i in $(seq 1 "$N"); do curl -s -o /dev/null -w "%{time_total}\n" "$1"; done | sort -n | awk '{a[NR]=$1} END{printf "p50=%.4fs p95=%.4fs max=%.4fs\n", a[int(NR*0.5)], a[int(NR*0.95)], a[NR]}'; }
sha() { shasum | cut -d' ' -f1; }

for ep in latest latest-cached; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/rates/$ep?provider=$PROVIDER")
  echo "/$ep -> HTTP $code"
  if [ "$code" != "200" ]; then echo "STOP: /$ep did not return 200, numbers would be meaningless"; exit 1; fi
done

docker exec currency-redis redis-cli DEL "latest:$PROVIDER" > /dev/null
echo "== correctness: DB vs cache miss vs cache hit"
a=$(curl -s "$BASE/api/v1/rates/latest?provider=$PROVIDER" | sha)
b=$(curl -s "$BASE/api/v1/rates/latest-cached?provider=$PROVIDER" | sha)
c=$(curl -s "$BASE/api/v1/rates/latest-cached?provider=$PROVIDER" | sha)
if [ "$a" = "$b" ] && [ "$b" = "$c" ]; then echo "IDENTICAL"; else echo "DIFFERENT"; fi
echo "redis TTL (seconds): $(docker exec currency-redis redis-cli TTL "latest:$PROVIDER")"
echo "redis value starts: $(docker exec currency-redis redis-cli GET "latest:$PROVIDER" | head -c 200)"

echo "== latency, $N sequential requests each (curl wall clock)"
echo "DB batched /latest:"; lat "$BASE/api/v1/rates/latest?provider=$PROVIDER"
echo "Redis /latest-cached:"; lat "$BASE/api/v1/rates/latest-cached?provider=$PROVIDER"

echo "== cache counters since app start"
hit=$(count hit); miss=$(count miss); stale=$(count stale); err=$(count error)
echo "hit=$hit miss=$miss stale=$stale error=$err"
awk -v h="$hit" -v m="$miss" -v s="$stale" 'BEGIN{t=h+m+s; if (t>0) printf "hit rate = %.1f%% (%d of %d lookups)\n", 100*h/t, h, t}'

echo "== freshness check: simulate a data load"
before=$(count stale)
$PSQL -c "INSERT INTO data_loads (dataset, file_name, rows_loaded) VALUES ('test','watermark-bump',0)"
curl -s -o /dev/null -w "request after fake load: HTTP %{http_code} %{time_total}s\n" "$BASE/api/v1/rates/latest-cached?provider=$PROVIDER"
after=$(count stale)
$PSQL -c "DELETE FROM data_loads WHERE dataset='test'"
echo "stale counter: $before -> $after (expected +1)"
