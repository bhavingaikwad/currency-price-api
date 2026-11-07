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

## M6 resilience results: Redis failure modes (2026-11-06)
- Redis container stopped: /latest-cached still returned HTTP 200 in about 16 ms via the DB fallback (cache errors counted and logged).
- Finding: overall /actuator/health went DOWN while Redis was down (Redis health indicator), which would let a load balancer kill healthy app containers over a cache outage.
- Fix: readiness health group limited to readinessState + db. Verified with Redis stopped: /actuator/health = DOWN, /actuator/health/readiness = UP; both UP again after Redis restart.
- Redis client timeouts set to 200 ms (read and connect). With Redis frozen (docker pause): HTTP 200 in 0.64 s, single sample. Likely one 200 ms wait on the cache read and one on the cache write, plus the DB query.
- NOT measured: frozen-Redis latency with default client timeouts, so no before/after claim is made for the timeouts.

## M7 measured results: duplicate-insert race (2026-11-06)
- Reproduced the race with a Testcontainers (real Postgres 16) test: 50 distinct rates, each submitted by 8 threads at once (400 insert attempts), check-then-insert implementation, no unique constraint. Result: 392 rows stored (342 duplicates). The existence check blocked only 8 of 400 attempts. Single run.
- Fix: unique index uq_rates_provider_pair_time (provider, source, target, rate_time) plus INSERT ... ON CONFLICT DO NOTHING (idempotent insert). Same test now requires exactly 50 rows and passes. A second test proves the database rejects a direct duplicate (DataIntegrityViolationException) even if application code skips the check.
- The unique index replaced the non-unique lookup index: index sizes unchanged (rates_pkey 116 MB, unique index 256 MB). Built successfully on the existing 5,434,647 rows (0 duplicate groups).
- Query plans after the change (dev database): per-pair latest = Index Scan Backward, 5 buffers, 0.051 ms (one cold sample). Whole batched latest-per-pair query = 0.611 ms with EXPLAIN ANALYZE (20 pairs, index-only scans, 0 heap fetches).
- Chose DO NOTHING (first write wins) because exchange rates and closed candles are historical facts.
- Not built: no HTTP endpoint for ingestion yet; cache eviction on ingest not wired yet.

## M8b: JWT authentication (2026-11-06)
- POST /api/v1/auth/token exchanges a username and password for an HS256 JWT (30 minute TTL, role claim). All /api/v1/rates reads require a Bearer token with role USER; /actuator details require ADMIN; /actuator/health and the token endpoint are open.
- Passwords are BCrypt-hashed; unknown users cost the same BCrypt time as wrong passwords. JWT secret must be at least 32 bytes or the app refuses to start; secrets come from environment variables (.env is gitignored).
- 401 and 403 use the same RFC 9457 Problem Details format as all other errors.
- 11 integration tests against a real Postgres (Testcontainers) cover: no token 401, garbage token 401, wrong password 401, unknown user 401, blank username 400, reader happy path and validation errors, open health endpoint, reader 403 vs admin 200 on /actuator/metrics. Also checked on the running app with curl: 401 / 200 / 403 / 200 as expected, token length 191.
- NOT built: user database or identity provider (two in-memory demo users), refresh tokens, token revocation (tokens are valid until expiry), role-protected write endpoint (ingestion still not exposed over HTTP).

## M8c: rate limiting (2026-11-06)
- Bucket4j 8.22.0 token-bucket filter, in-memory, buckets held in a size-bounded (10,000) Caffeine cache that expires idle entries after 10 minutes. Limits: login 5 per minute per IP (brute-force protection), other endpoints 60 per minute per authenticated user (per IP when unauthenticated), health checks exempt. Over the limit: HTTP 429 in the Problem Details format with a Retry-After header; allowed responses carry X-RateLimit-Remaining.
- 4 integration tests (real Postgres via Testcontainers, limits lowered by property): per-user limit with 429 body and headers, independent buckets per user, login limit per IP, health never limited.
- Checked on the running app: login attempts 1 to 5 returned 401, attempts 6 and 7 returned 429 with Retry-After 12 seconds (5 per minute refills one token every 12 seconds).
- Test suite after M8: 23 tests run, 1 skipped (opt-in live provider test), all passing.
- Limitations: buckets are per instance (not shared between multiple tasks); the client IP is the direct remote address, so behind a load balancer forwarded headers must be configured; not load-tested yet.

## M9: testing and coverage (all measured 2026-11-06)
- Test suite: 56 tests, 0 failures, 0 errors, 1 skipped. The skipped test is FrankfurterLiveTest, which calls the real Frankfurter API and only runs when RUN_LIVE_TESTS is set.
- JaCoCo 0.8.16 coverage: 76.6% line (229/299) and 55.4% branch (31/56) before the M9 tests; 97.3% line (291/299) and 87.5% branch (49/56) after.
- Coverage gate: the build fails below 95% line or 85% branch coverage. I verified the gate can fail by raising the line floor to 99% (build exit 1), then restoring it (exit 0).
- Integration tests run against real Postgres 16 and Redis 7 containers (Testcontainers): cache miss then hit with TTL <= 30 s, stale-watermark replacement, and a paused Redis container still returning HTTP 200 from the database.
- Unit tests (no Docker): cache logic with mocked Redis, query service, JWT issuing and validation, security property validation, error-handler paths, and Frankfurter provider error classification (4xx, 5xx, unreachable, unreadable body, empty body).
- Build time: `./mvnw clean verify` including all Testcontainers tests took 24.4 s on a MacBook Air (single run, Docker images already pulled).
- Caveats: line and branch coverage measure which code ran, not whether assertions are strong (no mutation testing was done). The 8 uncovered lines are the main() method, trivial accessors/constructors and health-endpoint boilerplate.

## M10: containerization (all measured 2026-11-06)
- Dockerfile: multi-stage (eclipse-temurin:21-jdk to build, eclipse-temurin:21-jre to run), Spring Boot layered jar, non-root user (uid 999), JVM flags MaxRAMPercentage=75 and ExitOnOutOfMemoryError. Tests are skipped in the image build (they run in CI in M11).
- Image size as reported by `docker image ls`: 616 MB disk usage, 179 MB content size. Layers: dependencies 73.8 MB, application code 65.5 kB.
- Build time: first build on this machine 33.2 s; rebuild with nothing changed 1.27 s (12 cached steps); rebuild after a one-line comment-only source change 4.65 s (10 cached steps). The change was a comment, so compiled classes were unchanged.
- docker-compose stack: Postgres 16 (shm_size 256 MB), Redis 7, and the app. The app starts only after Postgres and Redis are healthy; its health check calls /actuator/health/readiness. The app container is limited to 512 MiB.
- Startup time in the container: "Started CurrencyPriceApiApplication in 3.166 seconds" (MacBook Air, images already built).
- Memory shortly after startup and one request (`docker stats`, includes page cache): app 381.7 MiB of its 512 MiB limit, Postgres 37.5 MiB, Redis 9.7 MiB. Note for M12: size the ECS task memory above 512 MiB.
- Data persistence: after recreating the Postgres container, the named volume still held ECB 221,847 rows and BINANCE 5,212,800 rows, the same counts as the M3 load.
- Verified in the running container: login returns a 191-character token, the cached endpoint returns HTTP 200, readiness is UP, and the process runs as uid 999.
- Caveats: each timing is a single run; docker stats memory includes cache and is not a leak measurement.

## M11: CI/CD (all measured 2026-11-06)
- GitHub Actions workflow runs on every pull request and on pushes to main. Job `test` runs `./mvnw -B verify` (all 56 tests with Testcontainers plus the JaCoCo gate of 95% line / 85% branch) and uploads the coverage report. Job `image` runs only if `test` passes, builds the Docker image, and publishes it to ghcr.io/bhavingaikwad/currency-price-api (tags: commit SHA and latest) only on pushes to main.
- Run 1 (42339408667): test job 1m7s, image job 1m34s. Run 2 (42339709512, same code, runner pinned to ubuntu-24.04): test job 1m4s, image job 18s. One run each; the faster image job probably reflects the Docker build cache, but I did not isolate that.
- No secrets in CI beyond the built-in GITHUB_TOKEN; the repository holds none. Permissions are read-only by default and only the image job has packages:write.
- Before enabling CI, a clean-environment local build (dev Postgres and Redis stopped, app environment variables unset) passed: 56 tests, 29.1 s. That check found and fixed one test (the context-loads test) that depended on the local dev database.
- Runner image pinned to ubuntu-24.04 after GitHub announced that ubuntu-latest migrates to Ubuntu 26.
- Caveats: I verified the coverage gate fails locally (M9) but have not deliberately broken a CI run to watch the pipeline stop; timings are single runs.
