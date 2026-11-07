package com.bhavin.currencyapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.repository.RateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class RateIngestServiceTest {

    private final RateRepository repository = mock(RateRepository.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final RateIngestService service = new RateIngestService(repository, metrics);

    @Test
    void countsStoredAndDuplicateSeparately() {
        Rate rate = mock(Rate.class);
        when(repository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1, 0, 0);

        assertThat(service.ingestIfAbsent(rate)).isTrue();
        assertThat(service.ingestIfAbsent(rate)).isFalse();
        assertThat(service.ingestIfAbsent(rate)).isFalse();

        assertThat(metrics.counter("rates.ingest", "result", "stored").count()).isEqualTo(1.0);
        assertThat(metrics.counter("rates.ingest", "result", "duplicate").count()).isEqualTo(2.0);
    }
}
