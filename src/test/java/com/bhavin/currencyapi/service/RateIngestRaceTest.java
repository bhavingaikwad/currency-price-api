package com.bhavin.currencyapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.repository.RateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class RateIngestRaceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    RateIngestService ingestService;

    @Autowired
    RateRepository rateRepository;

    @BeforeEach
    void cleanTable() {
        rateRepository.deleteAll();
    }

    @Test
    void concurrentIngestOfTheSameRateStoresItExactlyOnce() throws Exception {
        int distinctRates = 50;
        int threads = 8;
        Instant base = Instant.parse("2030-01-01T00:00:00Z");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int k = 0; k < distinctRates; k++) {
                Instant time = base.plusSeconds(k);
                CyclicBarrier startTogether = new CyclicBarrier(threads);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    results.add(pool.submit(() -> {
                        Rate rate = new Rate("BTC", "USDT", time, new BigDecimal("100.5"), "TEST");
                        startTogether.await();
                        return ingestService.ingestIfAbsent(rate);
                    }));
                }
                for (Future<Boolean> result : results) {
                    result.get();
                }
            }
        } finally {
            pool.shutdown();
        }

        assertThat(rateRepository.count())
                .as("rows stored for %d distinct rates, each submitted by %d threads at once", distinctRates, threads)
                .isEqualTo(distinctRates);
    }

    @Test
    void databaseRejectsADuplicateEvenIfApplicationCodeSkipsTheCheck() {
        Instant time = Instant.parse("2030-02-01T00:00:00Z");
        rateRepository.saveAndFlush(new Rate("ETH", "USDT", time, new BigDecimal("1.0"), "TEST"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        rateRepository.saveAndFlush(new Rate("ETH", "USDT", time, new BigDecimal("2.0"), "TEST")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
