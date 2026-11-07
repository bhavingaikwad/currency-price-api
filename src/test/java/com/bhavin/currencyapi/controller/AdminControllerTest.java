package com.bhavin.currencyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bhavin.currencyapi.client.RateProvider;
import com.bhavin.currencyapi.domain.ProviderName;
import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.service.RateIngestService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminControllerTest {

    private final RateProvider ecb = mock(RateProvider.class);
    private final RateIngestService ingest = mock(RateIngestService.class);
    private final AdminController controller = new AdminController(List.of(ecb), ingest);
    private final AdminController.IngestRequest request =
            new AdminController.IngestRequest("USD", List.of("EUR", "GBP"));

    @Test
    void storesFetchedRatesAndCountsOnlyNewOnes() {
        Rate fresh = mock(Rate.class);
        Rate duplicate = mock(Rate.class);
        when(ecb.name()).thenReturn("ECB");
        when(ecb.fetchLatest("USD", List.of("EUR", "GBP"))).thenReturn(List.of(fresh, duplicate));
        when(ingest.ingestIfAbsent(fresh)).thenReturn(true);
        when(ingest.ingestIfAbsent(duplicate)).thenReturn(false);

        var response = controller.ingest(ProviderName.ECB, request);

        assertThat(response).isEqualTo(new AdminController.IngestResponse("ECB", 2, 1));
    }

    @Test
    void rejectsAProviderThatHasNoLiveSource() {
        when(ecb.name()).thenReturn("ECB");

        assertThatThrownBy(() -> controller.ingest(ProviderName.BINANCE, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
}
