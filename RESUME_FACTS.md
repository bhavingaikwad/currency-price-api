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
- ECB reference rates: TBD
- Binance 1-minute candles: TBD

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
