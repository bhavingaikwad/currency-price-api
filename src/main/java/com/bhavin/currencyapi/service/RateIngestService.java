package com.bhavin.currencyapi.service;

import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.repository.RateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateIngestService {

    private final RateRepository rateRepository;

    public RateIngestService(RateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    /**
     * Idempotent, race-safe insert. The unique index uq_rates_provider_pair_time is the real
     * guarantee; ON CONFLICT DO NOTHING makes the losing writer a no-op instead of an error.
     * Returns true if this call stored the rate.
     */
    @Transactional
    public boolean ingestIfAbsent(Rate rate) {
        return rateRepository.insertIfAbsent(
                rate.getSource(), rate.getTarget(), rate.getRateTime(), rate.getValue(), rate.getProvider()) == 1;
    }
}
