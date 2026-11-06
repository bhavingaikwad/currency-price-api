package com.bhavin.currencyapi.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rates.frankfurter")
public record FrankfurterProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
