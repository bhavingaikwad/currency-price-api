# Progress

| Milestone | Status | Date |
|-----------|--------|------|
| M0 Setup | Done (AWS account and billing alarm deferred to M12) | 2026-11-06 |
| M1 Skeleton | Done | 2026-11-06 |
| M2 Domain and provider client | Done | 2026-11-06 |
| M3 Data load | Done (2026-11-06) | 5.43M real rows loaded, DQ checks pass |
| M4 Naive N+1 endpoint | Done (2026-11-06) | 21 queries / ~2.4 s (BINANCE), 42 queries / ~4.2 s (ECB) per request |
| M5 Batched query + index | Done (2026-11-06) | 21 queries -> 1; BINANCE 2.4 s -> ~7 ms, ECB 4.2 s -> ~5 ms |
| M6 Redis cache-aside | Done (2026-11-06) | fresh + resilient; no single-client latency win (p50 4.3 vs 4.8 ms) |
| M7 Race + unique upsert | Done (2026-11-06) | 392 rows for 50 rates before; exactly 50 after |
| M8 Auth, rate limit, validation | In progress | 8a + 8b done (JWT); 8c rate limiting next |
| M9 Testing | Not started | |
| M10 Containerize | Not started | |
| M11 CI/CD | Not started | |
| M12 AWS deploy | Not started | |
| M13 Observability | Not started | |
| M14 Load test | Not started | |
| M15 Docs | Not started | |
| M16 Final handoff | Not started | |

## Known issues / to do later
- M10: add `shm_size: 256mb` to the postgres service in docker-compose.yml (default 64 MB /dev/shm made a parallel VACUUM fail).
- Loaders should end with `VACUUM (ANALYZE, PARALLEL 0) rates` (sets the visibility map after a bulk load).
- M10/M12: container and load-balancer health checks must use /actuator/health/readiness (db + app state), NOT /actuator/health, because overall health goes DOWN whenever Redis is down.
- RateIngestService is not exposed through an endpoint yet (planned M8, behind JWT). When it is, it must also evict the Redis key latest:<provider> after a successful insert.
