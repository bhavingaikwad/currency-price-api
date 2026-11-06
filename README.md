# Currency and Price Aggregation API

Spring Boot 4 / Java 21 service that aggregates exchange rates and crypto prices. Built as a performance-focused portfolio project (N+1 elimination, indexing, caching, concurrency control, load testing).

## Datasets (real data, not committed)

| Dataset | Source | Downloaded | Rows |
|---|---|---|---|
| ECB euro foreign exchange reference rates, full history | https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip | 2026-11-06 | 221,847 (1999-01-04 to 2026-11-06, 41 currency columns, N/A values dropped) |
| Binance spot 1-minute klines, 20 USDT pairs, 2026-01 to 2026-06 | https://data.binance.vision/data/spot/monthly/klines/ | 2026-11-06 | 5,212,800 (120 monthly files, each SHA-256 verified) |

Raw files live under `data/` and are gitignored. Reproduce with `scripts/download_binance.sh`, `scripts/load_ecb.sh`, `scripts/load_binance.sh` (Postgres `COPY`, idempotent via the `data_loads` table). Data-quality checks: `scripts/dq_checks.sql`.
Binance data terms: check https://data.binance.vision before redistributing anything derived from it.
