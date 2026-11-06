package com.bhavin.currencyapi.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bhavin.currencyapi.domain.Rate;

/**
 * Fetches European Central Bank reference rates through the Frankfurter API (v2).
 * Uses the ECB-only endpoint, because the plain endpoint blends several providers.
 */
@Component
public class FrankfurterRateProvider implements RateProvider {

    static final String PROVIDER_NAME = "ECB";

    /** Shape of one element in Frankfurter's JSON array response. */
    record FrankfurterRate(LocalDate date, String base, String quote, BigDecimal rate) {
    }

    private final RestClient restClient;

    public FrankfurterRateProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public List<Rate> fetchLatest(String base, Collection<String> quotes) {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("base currency must not be blank");
        }
        if (quotes == null || quotes.isEmpty()) {
            throw new IllegalArgumentException("at least one quote currency is required");
        }
        String baseCode = base.trim().toUpperCase(Locale.ROOT);
        String quoteCodes = String.join(",", quotes.stream()
                .map(q -> q.trim().toUpperCase(Locale.ROOT))
                .toList());

        FrankfurterRate[] body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/v2/providers/ecb/rates")
                            .queryParam("base", baseCode)
                            .queryParam("quotes", quoteCodes)
                            .build())
                    .retrieve()
                    .body(FrankfurterRate[].class);
        } catch (RestClientResponseException e) {
            RateProviderException.Kind kind = e.getStatusCode().is4xxClientError()
                    ? RateProviderException.Kind.CLIENT_ERROR
                    : RateProviderException.Kind.SERVER_ERROR;
            throw new RateProviderException(kind,
                    "ECB provider returned HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            throw new RateProviderException(RateProviderException.Kind.UNREACHABLE,
                    "ECB provider unreachable or timed out: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new RateProviderException(RateProviderException.Kind.BAD_RESPONSE,
                    "ECB provider response could not be read: " + e.getMessage(), e);
        }

        if (body == null) {
            throw new RateProviderException(RateProviderException.Kind.BAD_RESPONSE,
                    "ECB provider returned an empty body");
        }
        return Arrays.stream(body)
                .map(r -> new Rate(
                        r.base(),
                        r.quote(),
                        r.date().atStartOfDay(ZoneOffset.UTC).toInstant(),
                        r.rate(),
                        PROVIDER_NAME))
                .toList();
    }
}
