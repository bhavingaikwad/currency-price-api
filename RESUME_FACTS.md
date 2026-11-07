# Resume Facts (measured and real only)

Rule: nothing goes in this file unless I built it or measured it myself.
If a number is not measured yet, it says TBD.

## How this project was built
Built step by step with AI pair-programming guidance. I ran every command,
verified every output, and committed every change myself. I must be able to explain
every component before claiming it.

## Environment (verified 2026-11-06)
- Machine: MacBook Air, Apple Silicon (aarch64), macOS
- JDK: Temurin 21.0.13, Maven 3.10.0 (via wrapper), Git 2.56.0
- Spring Boot 4.1.2 (Spring Framework 7.0.10), Hibernate ORM 7.4.13, Flyway 12.4.0
- Docker 29.1.3 / Compose v2.40.3
- Postgres 16.15 (Docker image postgres:16, host port 5433), Redis 7 (Docker image redis:7)
- Terraform 1.17.1, k6 v2.3.0, AWS CLI 2.39.5 (AWS account not created yet, planned for M12)

## Datasets
- ECB reference rates: 221,847 rows, 1999-01-04 to 2026-11-06 (downloaded 2026-11-06)
- Binance 1-minute candles: 5,212,800 rows, 20 USDT pairs, 2026-01-01 to 2026-06-30 UTC (downloaded 2026-11-06)

## What exists so far (verified)
- Layered package layout: controller / service / repository / client / domain
- Flyway migrations V1 (baseline) and V2 (rates table); Hibernate ddl-auto=validate
- Rate entity (BigDecimal values, NUMERIC(28,12)), RateRepository
- RateProvider interface + FrankfurterRateProvider (ECB-only endpoint, 2s connect / 5s read timeouts, typed RateProviderException)
- Tests: 7 total (6 run by default, 1 live test skipped unless RUN_LIVE_TESTS=true), 0 failures
- Live ECB call via the client: EUR/USD 1.1165, EUR/GBP 0.83415 (2026-11-06); single cold call took 234 ms

## Design choices to explain
- rates table has no index and no unique constraint besides the primary key, on purpose:
  M4/M5 measure the query before and after the index, and M7 reproduces a duplicate-insert
  race before the unique constraint is added.
- Used the ECB-only Frankfurter endpoint because the default endpoint blends several providers
  (saw EUR/USD 1.1219 blended vs 1.1165 from ECB on the same day).
- Postgres container mapped to host port 5433: a local Postgres on this Mac already used 5432
  and was intercepting connections to localhost.

## Measurements (before/after)
(none yet)

## What I did NOT build
- No data loaded yet; no REST endpoints besides /health; no caching, auth, CI/CD or AWS yet.

## M3 measured facts (2026-11-06)
- Datasets loaded with Postgres COPY: ECB euro reference rate history (221,847 rows, 1999-01-04 to 2026-11-06) and Binance spot 1-minute klines (5,212,800 rows; 20 USDT pairs; 2026-01-01 to 2026-06-30 UTC; 120 monthly files, SHA-256 checksums verified). Total 5,434,647 rows in `rates`.
- Binance load: 120 files in 31.0 s wall clock (about 168k rows/s, includes ANALYZE and per-file transactions).
- `rates` table size: 554 MB total (heap + primary-key index only; no other indexes yet).
- Data-quality checks (scripts/dq_checks.sql): 0 non-positive values, 0 duplicate (source,target,rate_time,provider) groups, 0 ECB weekend rows, 0 missing minutes in all 20 Binance series, data_loads totals equal actual row counts.
- Row counts independently cross-checked against the raw files (awk count for ECB, wc -l for Binance); they matched exactly.
- Loads are idempotent: re-running skips files already recorded in `data_loads` (UNIQUE dataset + file_name).
- Not yet measured: query latency, index effects, cache hit rate (M4 onward).

## M4 measured baseline: naive N+1 endpoint (2026-11-06)
- Endpoint: GET /api/v1/rates/latest-naive?provider=... (1 query for pairs + 1 query per pair). Table: 5,434,647 rows, NO secondary indexes, local Docker Postgres 16 on a MacBook Air (Apple Silicon).
- BINANCE (20 pairs): 21 SQL statements per request (counted from Hibernate SQL log); 3 sequential curl requests took 2.68 s, 2.40 s, 2.41 s (later single request 2.65 s).
- ECB (41 pairs): 42 SQL statements per request; 3 requests took 4.21 s, 4.18 s, 4.15 s (later single request 4.19 s).
- Each per-pair query took about 100 ms (log timestamps), consistent with a full table scan of the unindexed table.
- Caveats: single client, curl wall-clock time, no warm-up control, no load test yet. Proper p50/p95/p99 comes from k6 in M14.
- Not yet fixed: this is the BEFORE number. M5 fixes N+1 (batched query) and indexing, measured separately.

## M5 measured results: index + batched query (2026-11-06)
Same machine, same 5,434,647-row table, single client with curl, 3 requests per case (steady-state = 3rd request).
- Index added: idx_rates_provider_pair_time ON rates (provider, source, target, rate_time DESC). Size 256 MB (primary key 116 MB). Write/storage cost accepted.
- Per-pair "latest" query plan (EXPLAIN ANALYZE, BUFFERS): BEFORE Parallel Seq Scan over 5.4M rows, 56,128 buffers (about 440 MB), 108 ms warm (249 ms cold). AFTER Index Scan, 5 buffers, 0.023 ms.
- Distinct-pairs query: Parallel Seq Scan before (292 ms) and after (328 ms). The index does not help because ~96% of the table is BINANCE. Postgres 16 has no skip scan.
- Fix 1 (index only, N+1 still present): BINANCE 2.41 s -> 0.24 s (21 queries); ECB 4.15 s -> 0.037 s (42 queries).
- Fix 2 (one batched query: recursive CTE "loose index scan" + LATERAL, via /api/v1/rates/latest): 1 SQL statement per request. BINANCE steady-state 0.0066 s; ECB 0.0050 s.
- End to end, BINANCE 2.41 s -> 0.0066 s (about 360x), ECB 4.15 s -> 0.0050 s (about 830x); 21 and 42 queries -> 1.
- Batched and naive endpoints return identical data for BINANCE (20 rows) and ECB (41 rows); unknown provider returns [].
- Caveats: curl wall-clock, single client, no concurrency; p50/p95/p99 under load come from k6 in M14. Run 1 after a restart is slower (JVM and cache warm-up).

## M6 measured results: Redis cache-aside (2026-11-06)
- Cache-aside on /api/v1/rates/latest-cached: Redis 7, 30 s TTL, plus a freshness check (data_loads watermark compared on every hit). Hit/miss/stale/error counters exposed through Micrometer.
- Correctness: DB, cache-miss and cache-hit responses are byte-identical (SHA check).
- Freshness check: after inserting a data_loads row, the next request counted 1 stale lookup, refreshed from the DB, and returned HTTP 200.
- Latency, BINANCE, 100 sequential curl requests, single client: /latest (DB, batched) p50 4.8 ms, p95 5.3 ms, max 6.3 ms; /latest-cached p50 4.3 ms, p95 6.5 ms, max 45 ms. Conclusion: at single-client level the cache gives no meaningful latency win, because the batched indexed query is already fast. Behaviour under concurrent load is measured in M14.
- Counters in that test: 101 hits, 2 misses (98.1%), an artifact of repeating one request, NOT a production hit rate.
- Not yet verified: behaviour when Redis is down; cache benefit under concurrency.
