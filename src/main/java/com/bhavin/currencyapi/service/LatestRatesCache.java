package com.bhavin.currencyapi.service;

import com.bhavin.currencyapi.domain.RateView;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache-aside for "latest rate per pair".
 * Freshness = TTL + a data_loads watermark compared on every hit.
 * Redis problems never break the endpoint: any cache failure falls back to the database.
 */
@Service
public class LatestRatesCache {

    private static final Logger log = LoggerFactory.getLogger(LatestRatesCache.class);
    private static final String KEY_PREFIX = "latest:";

    record CacheEntry(long watermark, List<RateView> rates) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final RateQueryService rates;
    private final MeterRegistry metrics;
    private final Duration ttl;

    public LatestRatesCache(StringRedisTemplate redis, ObjectMapper json, RateQueryService rates,
                            MeterRegistry metrics, @Value("${rates.cache.ttl:30s}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.rates = rates;
        this.metrics = metrics;
        this.ttl = ttl;
    }

    public List<RateView> latest(String provider) {
        String key = KEY_PREFIX + provider;
        long watermark = rates.currentWatermark();

        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                CacheEntry entry = json.readValue(cached, CacheEntry.class);
                if (entry.watermark() == watermark) {
                    count("hit");
                    return entry.rates();
                }
                count("stale");
            } else {
                count("miss");
            }
        } catch (RuntimeException e) {
            count("error");
            log.warn("cache read failed, falling back to database: {}", e.toString());
        }

        List<RateView> fresh = rates.latestPerPair(provider);

        if (!fresh.isEmpty()) {
            try {
                redis.opsForValue().set(key, json.writeValueAsString(new CacheEntry(watermark, fresh)), ttl);
            } catch (RuntimeException e) {
                count("error");
                log.warn("cache write failed: {}", e.toString());
            }
        }
        return fresh;
    }

    private void count(String result) {
        metrics.counter("rates.cache", "result", result).increment();
    }
}
