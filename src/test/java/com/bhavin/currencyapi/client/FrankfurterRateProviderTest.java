package com.bhavin.currencyapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import com.bhavin.currencyapi.domain.Rate;

class FrankfurterRateProviderTest {

    private static final RequestMatcher ECB_PATH = request ->
            assertThat(request.getURI().getPath()).isEqualTo("/v2/providers/ecb/rates");

    private MockRestServiceServer server;
    private FrankfurterRateProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.frankfurter.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new FrankfurterRateProvider(builder.build());
    }

    @Test
    void mapsProviderResponseToRates() {
        server.expect(method(HttpMethod.GET))
                .andExpect(ECB_PATH)
                .andExpect(queryParam("base", "EUR"))
                .andExpect(queryParam("quotes", "USD,GBP"))
                .andRespond(withSuccess("""
                        [{"date":"2026-11-06","base":"EUR","quote":"GBP","rate":0.83415},
                         {"date":"2026-11-06","base":"EUR","quote":"USD","rate":1.1165}]
                        """, MediaType.APPLICATION_JSON));

        List<Rate> rates = provider.fetchLatest("eur", List.of("usd", "gbp"));

        assertThat(rates).hasSize(2);
        Rate gbp = rates.get(0);
        assertThat(gbp.getSource()).isEqualTo("EUR");
        assertThat(gbp.getTarget()).isEqualTo("GBP");
        assertThat(gbp.getValue()).isEqualByComparingTo(new BigDecimal("0.83415"));
        assertThat(gbp.getRateTime()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
        assertThat(gbp.getProvider()).isEqualTo("ECB");
        server.verify();
    }

    @Test
    void unknownCurrencyIsReportedAsClientError() {
        server.expect(method(HttpMethod.GET))
                .andExpect(ECB_PATH)
                .andRespond(withStatus(HttpStatusCode.valueOf(422))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":422,\"message\":\"invalid currency: ABC\"}"));

        assertThatThrownBy(() -> provider.fetchLatest("EUR", List.of("ABC")))
                .isInstanceOfSatisfying(RateProviderException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(RateProviderException.Kind.CLIENT_ERROR);
                    assertThat(e.getMessage()).contains("invalid currency");
                });
    }

    @Test
    void providerOutageIsReportedAsServerError() {
        server.expect(method(HttpMethod.GET))
                .andExpect(ECB_PATH)
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.fetchLatest("EUR", List.of("USD")))
                .isInstanceOfSatisfying(RateProviderException.class,
                        e -> assertThat(e.getKind()).isEqualTo(RateProviderException.Kind.SERVER_ERROR));
    }

    @Test
    void unreadableBodyIsReportedAsBadResponse() {
        server.expect(method(HttpMethod.GET))
                .andExpect(ECB_PATH)
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchLatest("EUR", List.of("USD")))
                .isInstanceOfSatisfying(RateProviderException.class,
                        e -> assertThat(e.getKind()).isEqualTo(RateProviderException.Kind.BAD_RESPONSE));
    }

    @Test
    void blankBaseIsRejectedBeforeAnyHttpCall() {
        assertThatThrownBy(() -> provider.fetchLatest(" ", List.of("USD")))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
