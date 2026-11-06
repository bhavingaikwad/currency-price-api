package com.bhavin.currencyapi.service;

import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.repository.PairKey;
import com.bhavin.currencyapi.repository.RateRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateQueryService {

    private final RateRepository rateRepository;

    public RateQueryService(RateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    /**
     * NAIVE on purpose (M4): 1 query for the pairs + 1 query PER pair = N+1 round trips.
     * Replaced by a batched query in M5; kept so we can compare before and after.
     */
    @Transactional(readOnly = true)
    public List<Rate> latestPerPairNaive(String provider) {
        List<PairKey> pairs = rateRepository.findDistinctPairs(provider);
        List<Rate> result = new ArrayList<>();
        for (PairKey pair : pairs) {
            rateRepository
                    .findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc(
                            pair.source(), pair.target(), provider)
                    .ifPresent(result::add);
        }
        return result;
    }
}
