package com.bhavin.currencyapi.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    public record HealthResponse(String status, String service, Instant timestamp) {
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "currency-price-api", Instant.now());
    }
}
