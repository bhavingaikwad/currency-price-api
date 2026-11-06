\echo '== 1. Row counts by provider'
SELECT provider, count(*) AS row_count, min(rate_time) AS first_time, max(rate_time) AS last_time
FROM rates GROUP BY provider ORDER BY provider;

\echo '== 2. Non-positive values (expect 0)'
SELECT count(*) AS non_positive FROM rates WHERE value <= 0;

\echo '== 3. Duplicate (source,target,rate_time,provider) groups (expect 0)'
SELECT count(*) AS duplicate_groups FROM (
  SELECT 1 FROM rates GROUP BY source, target, rate_time, provider HAVING count(*) > 1
) d;

\echo '== 4. Binance gaps per symbol (missing_minutes = span+1 - rows)'
SELECT source AS symbol, count(*) AS row_count,
       (extract(epoch FROM (max(rate_time) - min(rate_time))) / 60)::bigint + 1 AS expected_rows,
       (extract(epoch FROM (max(rate_time) - min(rate_time))) / 60)::bigint + 1 - count(*) AS missing_minutes
FROM rates WHERE provider = 'BINANCE'
GROUP BY source ORDER BY missing_minutes DESC, source LIMIT 25;

\echo '== 5. ECB rows on weekends (expect 0)'
SELECT count(*) AS weekend_rows FROM rates
WHERE provider = 'ECB' AND extract(isodow FROM rate_time AT TIME ZONE 'UTC') IN (6, 7);

\echo '== 6. data_loads vs actual rows (diff must be 0)'
SELECT m.provider, m.logged AS logged_in_data_loads, a.actual AS actual_in_rates, a.actual - m.logged AS diff
FROM (SELECT CASE dataset WHEN 'binance_1m' THEN 'BINANCE' WHEN 'ecb_history' THEN 'ECB' END AS provider,
             sum(rows_loaded) AS logged
      FROM data_loads GROUP BY 1) m
JOIN (SELECT provider, count(*) AS actual FROM rates GROUP BY provider) a USING (provider)
ORDER BY m.provider;

\echo '== 7. Table size'
SELECT pg_size_pretty(pg_total_relation_size('rates')) AS rates_total_size;
