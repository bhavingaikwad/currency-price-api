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
| M8 Auth, rate limit, validation | Done (2026-11-06) | JWT + roles, Bucket4j limits, RFC 9457 errors, 15 integration tests |
| M9 Testing | Done (2026-11-06) | 56 tests (1 live-API test skipped by design), JaCoCo 97.3% line / 87.5% branch, coverage gate enforced in the build |
| M10 Containerize | Done (2026-11-06) | multi-stage non-root Dockerfile with layered jar, full compose stack (app + Postgres + Redis) with health checks |
| M11 CI/CD | Done (2026-11-06) | GitHub Actions: tests + coverage gate on every push/PR, image published to GHCR from main |
| M12 AWS deploy | Done, torn down (2026-11-07) | Terraform: 45 resources on AWS (VPC, RDS, ElastiCache, ECR, ECS Fargate, ALB, SSM), deployed and verified through the load balancer, then destroyed; real cost check pending |
| M13 Observability | Done locally (2026-11-07) | JSON log profile, rates.ingest and rates.cache metrics, CloudWatch dashboard + 2 alarms defined in Terraform (planned, not deployed); 59 tests, 97.5% line / 88.7% branch |
| M14 Load test | Done (2026-11-07) | k6, 10 users for 30 s on BINANCE (5.2M rows): naive p95 2.75 s, single-query + index p95 4.8 ms, cached p95 3.87 ms (one local run) |
| M15 Docs | Done (2026-11-07) | README with results table and AWS diagram, 3 decision records, runbook, OpenAPI spec |
| M16 Final handoff | Not started | |

## Known issues / to do later
- Loaders should end with `VACUUM (ANALYZE, PARALLEL 0) rates` (sets the visibility map after a bulk load).
- Done in M12: the load balancer health check uses /actuator/health/readiness, not /actuator/health.
- Done in M12: POST /api/v1/admin/ingest exists (ADMIN role only). No cache eviction needed: the latest-rates cache uses a data_loads watermark plus a 30 s TTL.
- Rate-limit buckets are in-memory per instance and key on the remote IP (forwarded headers are enabled). Fine for one task; use Redis-backed Bucket4j buckets before running more than one.
