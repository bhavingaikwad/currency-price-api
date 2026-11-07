package com.bhavin.currencyapi.service;

import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.repository.RateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateIngestService {

    private final RateRepository rateRepository;
    private final MeterRegistry metrics;

    public RateIngestService(RateRepository rateRepository, MeterRegistry metrics) {
        this.rateRepository = rateRepository;
        this.metrics = metrics;
    }

    /**
     * Idempotent, race-safe insert. The unique index uq_rates_provider_pair_time is the real
     * guarantee; ON CONFLICT DO NOTHING makes the losing writer a no-op instead of an error.
     * Returns true if this call stored the rate. Counted as rates.ingest{result=stored|duplicate}.
     */
    @Transactional
    public boolean ingestIfAbsent(Rate rate) {
        boolean stored = rateRepository.insertIfAbsent(
                rate.getSource(), rate.getTarget(), rate.getRateTime(), rate.getValue(), rate.getProvider()) == 1;
        metrics.counter("rates.ingest", "result", stored ? "stored" : "duplicate").increment();
        return stored;
    }
}
