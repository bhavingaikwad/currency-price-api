package com.bhavin.currencyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhavin.currencyapi.domain.RateView;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class LatestRatesCacheTest {

    private static final String KEY = "latest:BINANCE";
    private static final List<RateView> SAMPLE = List.of(new RateView(
            "BTC", "USDT", new BigDecimal("100.500000000000"), Instant.parse("2026-06-30T23:59:00Z"), "BINANCE"));

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final RateQueryService rates = mock(RateQueryService.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final JsonMapper json = JsonMapper.builder().build();
    private final LatestRatesCache cache = new LatestRatesCache(redis, json, rates, metrics, Duration.ofSeconds(30));

    @BeforeEach
    void wire() {
        when(redis.opsForValue()).thenReturn(ops);
        when(rates.currentWatermark()).thenReturn(7L);
        when(rates.latestPerPair("BINANCE")).thenReturn(SAMPLE);
    }

    private double count(String result) {
        return metrics.counter("rates.cache", "result", result).count();
    }

    private String cachedJson(long watermark) {
        return json.writeValueAsString(new LatestRatesCache.CacheEntry(watermark, SAMPLE));
    }

    @Test
    void missReadsTheDatabaseAndStoresTheEntryWithTtlAndWatermark() {
        when(ops.get(KEY)).thenReturn(null);

        List<RateView> result = cache.latest("BINANCE");

        assertThat(result).hasSize(1);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(KEY), stored.capture(), eq(Duration.ofSeconds(30)));
        assertThat(stored.getValue()).contains("\"watermark\":7").contains("\"source\":\"BTC\"");
        assertThat(count("miss")).isEqualTo(1.0);
    }

    @Test
    void hitWithTheSameWatermarkSkipsTheDatabase() {
        when(ops.get(KEY)).thenReturn(cachedJson(7));

        List<RateView> result = cache.latest("BINANCE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo("BTC");
        assertThat(result.get(0).value()).isEqualByComparingTo("100.5");
        verify(rates, never()).latestPerPair(anyString());
        assertThat(count("hit")).isEqualTo(1.0);
    }

    @Test
    void staleWatermarkRefreshesFromTheDatabase() {
        when(ops.get(KEY)).thenReturn(cachedJson(6));

        cache.latest("BINANCE");

        verify(rates).latestPerPair("BINANCE");
        verify(ops).set(eq(KEY), anyString(), any(Duration.class));
        assertThat(count("stale")).isEqualTo(1.0);
        assertThat(count("hit")).isZero();
    }

    @Test
    void redisReadFailureFallsBackToTheDatabase() {
        when(ops.get(KEY)).thenThrow(new RuntimeException("redis down"));

        List<RateView> result = cache.latest("BINANCE");

        assertThat(result).hasSize(1);
        assertThat(count("error")).isEqualTo(1.0);
    }

    @Test
    void redisWriteFailureStillReturnsFreshData() {
        when(ops.get(KEY)).thenReturn(null);
        doThrow(new RuntimeException("redis down")).when(ops).set(anyString(), anyString(), any(Duration.class));

        List<RateView> result = cache.latest("BINANCE");

        assertThat(result).hasSize(1);
        assertThat(count("miss")).isEqualTo(1.0);
        assertThat(count("error")).isEqualTo(1.0);
    }

    @Test
    void corruptCacheEntryIsTreatedAsAnErrorNotACrash() {
        when(ops.get(KEY)).thenReturn("this is not json");

        List<RateView> result = cache.latest("BINANCE");

        assertThat(result).hasSize(1);
        assertThat(count("error")).isEqualTo(1.0);
    }

    @Test
    void emptyResultsAreNotCached() {
        when(ops.get(KEY)).thenReturn(null);
        when(rates.latestPerPair("BINANCE")).thenReturn(List.of());

        assertThat(cache.latest("BINANCE")).isEmpty();

        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
