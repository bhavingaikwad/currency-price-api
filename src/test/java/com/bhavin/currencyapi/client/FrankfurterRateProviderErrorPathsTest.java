package com.bhavin.currencyapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bhavin.currencyapi.domain.Rate;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FrankfurterRateProviderErrorPathsTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://frankfurter.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FrankfurterRateProvider provider = new FrankfurterRateProvider(builder.build());

    private void assertKind(Runnable call, RateProviderException.Kind kind) {
        assertThatThrownBy(call::run)
                .isInstanceOf(RateProviderException.class)
                .extracting("kind")
                .isEqualTo(kind);
    }

    @Test
    void blankBaseIsRejectedBeforeAnyRequest() {
        assertThatThrownBy(() -> provider.fetchLatest("  ", List.of("USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingOrEmptyQuotesAreRejectedBeforeAnyRequest() {
        assertThatThrownBy(() -> provider.fetchLatest("EUR", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.fetchLatest("EUR", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codesAreNormalizedAndResponseIsMappedToRates() {
        server.expect(queryParam("base", "EUR"))
                .andExpect(queryParam("quotes", "USD,JPY"))
                .andRespond(withSuccess(
                        "[{\"date\":\"2026-11-06\",\"base\":\"EUR\",\"quote\":\"USD\",\"rate\":1.1165}]",
                        MediaType.APPLICATION_JSON));

        List<Rate> rates = provider.fetchLatest(" eur ", List.of("usd", " jpy"));

        assertThat(rates).extracting("source", "target", "provider", "rateTime")
                .containsExactly(tuple("EUR", "USD", "ECB", Instant.parse("2026-11-06T00:00:00Z")));
        server.verify();
    }

    @Test
    void clientErrorStatusIsClassifiedAsClientError() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertKind(() -> provider.fetchLatest("EUR", List.of("USD")), RateProviderException.Kind.CLIENT_ERROR);
    }

    @Test
    void serverErrorStatusIsClassifiedAsServerError() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertKind(() -> provider.fetchLatest("EUR", List.of("USD")), RateProviderException.Kind.SERVER_ERROR);
    }

    @Test
    void ioFailureIsClassifiedAsUnreachable() {
        server.expect(anything()).andRespond(withException(new IOException("connection reset")));
        assertKind(() -> provider.fetchLatest("EUR", List.of("USD")), RateProviderException.Kind.UNREACHABLE);
    }

    @Test
    void unreadableBodyIsClassifiedAsBadResponse() {
        server.expect(anything()).andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));
        assertKind(() -> provider.fetchLatest("EUR", List.of("USD")), RateProviderException.Kind.BAD_RESPONSE);
    }

    @Test
    void emptyBodyIsClassifiedAsBadResponse() {
        server.expect(anything()).andRespond(withSuccess());
        assertKind(() -> provider.fetchLatest("EUR", List.of("USD")), RateProviderException.Kind.BAD_RESPONSE);
    }
}
