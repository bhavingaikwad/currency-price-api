package com.bhavin.currencyapi.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.bhavin.currencyapi.domain.Rate;

/** Calls the real Frankfurter API. Skipped unless RUN_LIVE_TESTS=true. */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_TESTS", matches = "true")
class FrankfurterLiveTest {

    @Test
    void fetchesRealEcbRates() {
        FrankfurterProperties props = new FrankfurterProperties(
                "https://api.frankfurter.dev", Duration.ofSeconds(2), Duration.ofSeconds(5));
        FrankfurterRateProvider provider =
                new FrankfurterRateProvider(new FrankfurterClientConfig().frankfurterRestClient(props));

        long start = System.nanoTime();
        List<Rate> rates = provider.fetchLatest("EUR", List.of("USD", "GBP"));
        long millis = (System.nanoTime() - start) / 1_000_000;

        rates.forEach(r -> System.out.println(
                "LIVE " + r.getSource() + "/" + r.getTarget() + " = " + r.getValue() + " at " + r.getRateTime()));
        System.out.println("LIVE call took " + millis + " ms");

        assertThat(rates).hasSize(2);
        assertThat(rates).allSatisfy(r -> assertThat(r.getValue()).isGreaterThan(BigDecimal.ZERO));
    }
}
