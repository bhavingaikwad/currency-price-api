# ADR 1: One indexed query instead of N+1

Status: accepted

## Context

The first version of the latest-rates endpoint ran a query for the distinct currency pairs of a provider and then one query per pair to find its newest rate (an N+1 pattern). Under the k6 load test on 5.2 million Binance rows it served 6.5 requests per second with a p95 of 2.75 s.

## Decision

Fetch everything in one round trip (`RateRepository.findLatestPerPair`). A recursive CTE walks the unique index `uq_rates_provider_pair_time (provider, source, target, rate_time)` from one pair to the next (a loose index scan, because PostgreSQL 16 has no skip scan), and a `LATERAL` subquery reads the newest row of each pair from the same index.

## Consequences

- Measured under the same k6 load: p95 fell from 2.75 s to 4.8 ms and throughput rose from 6.5 to 3,945 requests per second.
- One index does two jobs: it enforces uniqueness of a rate and it serves the latest-per-pair lookup.
- The query is native PostgreSQL SQL, so it is less portable than a JPA method.
- The naive endpoint is kept in the code on purpose, as the baseline for the load test.
