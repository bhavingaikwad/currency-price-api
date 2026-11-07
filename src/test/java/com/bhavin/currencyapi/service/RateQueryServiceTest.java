package com.bhavin.currencyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.domain.RateView;
import com.bhavin.currencyapi.repository.PairKey;
import com.bhavin.currencyapi.repository.RateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RateQueryServiceTest {

    private static final Instant TIME = Instant.parse("2026-06-30T23:59:00Z");

    private final RateRepository repository = mock(RateRepository.class);
    private final RateQueryService service = new RateQueryService(repository);

    @Test
    void naivePathIssuesOneQueryPerPairPlusOne() {
        when(repository.findDistinctPairs("BINANCE"))
                .thenReturn(List.of(new PairKey("BTC", "USDT"), new PairKey("ETH", "USDT"), new PairKey("XRP", "USDT")));
        when(repository.findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc("BTC", "USDT", "BINANCE"))
                .thenReturn(Optional.of(new Rate("BTC", "USDT", TIME, new BigDecimal("100"), "BINANCE")));
        when(repository.findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc("ETH", "USDT", "BINANCE"))
                .thenReturn(Optional.empty());
        when(repository.findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc("XRP", "USDT", "BINANCE"))
                .thenReturn(Optional.of(new Rate("XRP", "USDT", TIME, new BigDecimal("0.5"), "BINANCE")));

        List<RateView> result = service.latestPerPairNaive("BINANCE");

        assertThat(result).extracting(RateView::source).containsExactly("BTC", "XRP");
        verify(repository, times(3)).findFirstBySourceAndTargetAndProviderOrderByRateTimeDesc(
                anyString(), anyString(), anyString());
    }

    @Test
    void batchedPathMapsRepositoryRowsToViews() {
        when(repository.findLatestPerPair("ECB"))
                .thenReturn(List.of(new Rate("EUR", "USD", TIME, new BigDecimal("1.1165"), "ECB")));

        List<RateView> result = service.latestPerPair("ECB");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).target()).isEqualTo("USD");
        assertThat(result.get(0).value()).isEqualByComparingTo("1.1165");
        assertThat(result.get(0).provider()).isEqualTo("ECB");
    }

    @Test
    void watermarkDelegatesToTheRepository() {
        when(repository.dataLoadsWatermark()).thenReturn(121L);

        assertThat(service.currentWatermark()).isEqualTo(121L);
    }
}
