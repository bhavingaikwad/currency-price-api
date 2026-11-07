CREATE INDEX idx_rates_provider_pair_time
    ON rates (provider, source, target, rate_time DESC);
