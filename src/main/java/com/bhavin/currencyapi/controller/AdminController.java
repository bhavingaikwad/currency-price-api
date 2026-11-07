package com.bhavin.currencyapi.controller;

import com.bhavin.currencyapi.client.RateProvider;
import com.bhavin.currencyapi.domain.ProviderName;
import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.service.RateIngestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Operator endpoint (ADMIN role): pulls the latest rates from a live provider and stores them. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    public record IngestRequest(@NotBlank String base, @NotEmpty List<@NotBlank String> quotes) {
    }

    public record IngestResponse(String provider, int fetched, int stored) {
    }

    private final List<RateProvider> providers;
    private final RateIngestService ingestService;

    public AdminController(List<RateProvider> providers, RateIngestService ingestService) {
        this.providers = providers;
        this.ingestService = ingestService;
    }

    @PostMapping("/ingest")
    public IngestResponse ingest(@RequestParam ProviderName provider, @Valid @RequestBody IngestRequest request) {
        RateProvider source = providers.stream()
                .filter(p -> p.name().equals(provider.name()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No live source for provider " + provider));
        List<Rate> fetched = source.fetchLatest(request.base(), request.quotes());
        int stored = 0;
        for (Rate rate : fetched) {
            if (ingestService.ingestIfAbsent(rate)) {
                stored++;
            }
        }
        return new IngestResponse(provider.name(), fetched.size(), stored);
    }
}
