# ADR 2: Cache-aside with a freshness watermark

Status: accepted

## Context

The dataset is loaded in bulk by scripts that run outside the application (`COPY` into PostgreSQL), so the application cannot evict a cache entry when new data arrives. A TTL alone would keep serving old rates for as long as the TTL lasts after every load.

## Decision

`LatestRatesCache` uses cache-aside on Redis. Each entry stores the result together with the `data_loads` watermark (`MAX(id)`) that was current when it was written. On every request the service reads the current watermark from PostgreSQL: a matching entry is a hit; a different watermark counts as stale and triggers a fresh query. Entries also expire after 30 s (`rates.cache.ttl`). Empty results are never cached, and any Redis error is caught and falls back to PostgreSQL.

## Consequences

- Bulk loads show up immediately without any eviction code, and a Redis outage degrades to slower answers instead of errors.
- Every request still makes one cheap PostgreSQL call for the watermark, which is why the measured gain over the indexed query is modest (p95 4.8 ms to 3.87 ms).
- Rows added through the admin ingest endpoint do not change the watermark, so the cached view can lag by up to the 30 s TTL.
- Cache outcomes are counted as `rates.cache{result=hit|miss|stale|error}`.
