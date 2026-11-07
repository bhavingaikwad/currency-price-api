CREATE UNIQUE INDEX uq_rates_provider_pair_time
    ON rates (provider, source, target, rate_time);

DROP INDEX idx_rates_provider_pair_time;
