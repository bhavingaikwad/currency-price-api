package com.bhavin.currencyapi.repository;

import com.bhavin.currencyapi.domain.Rate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RateRepository extends JpaRepository<Rate, Long> {

    @Query("select distinct new com.bhavin.currencyapi.repository.PairKey(r.source, r.target) "
            + "from Rate r where r.provider = :provider")
    List<PairKey> findDistinctPairs(@Param("provider") String provider);

    Optional<Rate> findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc(
            String source, String target, String provider);
}
