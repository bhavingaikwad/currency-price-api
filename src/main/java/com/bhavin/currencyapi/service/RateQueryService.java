package com.bhavin.currencyapi.service;

import com.bhavin.currencyapi.domain.RateView;
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

    @Transactional(readOnly = true)
    public List<RateView> latestPerPairNaive(String provider) {
        List<PairKey> pairs = rateRepository.findDistinctPairs(provider);
        List<RateView> result = new ArrayList<>();
        for (PairKey pair : pairs) {
            rateRepository
                    .findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc(pair.source(), pair.target(), provider)
                    .map(RateView::from)
                    .ifPresent(result::add);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<RateView> latestPerPair(String provider) {
        return rateRepository.findLatestPerPair(provider).stream().map(RateView::from).toList();
    }

    @Transactional(readOnly = true)
    public long currentWatermark() {
        return rateRepository.dataLoadsWatermark();
    }
}
