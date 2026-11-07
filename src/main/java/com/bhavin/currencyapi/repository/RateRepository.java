package com.bhavin.currencyapi.repository;

import com.bhavin.currencyapi.domain.Rate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RateRepository extends JpaRepository<Rate, Long> {

    // --- naive N+1 path (M4), kept for before/after comparison ---
    @Query("select distinct new com.bhavin.currencyapi.repository.PairKey(r.source, r.target) "
            + "from Rate r where r.provider = :provider")
    List<PairKey> findDistinctPairs(@Param("provider") String provider);

    Optional<Rate> findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc(
            String source, String target, String provider);

    // --- batched path (M5): ONE round trip ---
    // The recursive CTE hops from pair to pair using uq_rates_provider_pair_time (a "loose index
    // scan", because Postgres 16 has no skip scan), then LATERAL fetches the newest row per pair.
    @Query(value = """
            WITH RECURSIVE pairs AS (
                (SELECT source, target FROM rates
                 WHERE provider = :provider ORDER BY source, target LIMIT 1)
                UNION ALL
                SELECT n.source, n.target
                FROM pairs p
                CROSS JOIN LATERAL (
                    SELECT source, target FROM rates
                    WHERE provider = :provider AND (source, target) > (p.source, p.target)
                    ORDER BY source, target LIMIT 1
                ) n
            )
            SELECT r.*
            FROM pairs p
            CROSS JOIN LATERAL (
                SELECT * FROM rates
                WHERE provider = :provider AND source = p.source AND target = p.target
                ORDER BY rate_time DESC LIMIT 1
            ) r
            ORDER BY r.source, r.target
            """, nativeQuery = true)
    List<Rate> findLatestPerPair(@Param("provider") String provider);

    @Query(value = "SELECT COALESCE(MAX(id), 0) FROM data_loads", nativeQuery = true)
    long dataLoadsWatermark();


    // Idempotent insert (M7): the unique index decides; the loser gets 0 rows affected.
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            INSERT INTO rates (source, target, rate_time, value, provider)
            VALUES (:source, :target, :rateTime, :value, :provider)
            ON CONFLICT (provider, source, target, rate_time) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("source") String source,
                       @Param("target") String target,
                       @Param("rateTime") java.time.Instant rateTime,
                       @Param("value") java.math.BigDecimal value,
                       @Param("provider") String provider);
}
