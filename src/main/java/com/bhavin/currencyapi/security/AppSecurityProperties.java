package com.bhavin.currencyapi.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Security settings. The app refuses to start if the JWT secret is missing or too short. */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(String jwtSecret, String readerPassword, String adminPassword, Duration tokenTtl) {

    public AppSecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.security.jwt-secret must be at least 32 bytes");
        }
        if (readerPassword == null || readerPassword.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalArgumentException("app.security.reader-password and admin-password must be set");
        }
        if (tokenTtl == null) {
            tokenTtl = Duration.ofMinutes(30);
        }
    }
}
