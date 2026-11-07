package com.bhavin.currencyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.ratelimit.login-per-minute=3", "app.ratelimit.api-per-minute=5"})
@Testcontainers
class RateLimitTest {

    private static final String LATEST = "/api/v1/rates/latest?provider=BINANCE";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Value("${local.server.port}")
    int port;

    @Autowired
    JwtEncoder jwtEncoder;

    private final HttpClient client = HttpClient.newHttpClient();

    /** Mints a token directly, so these tests do not consume the login rate limit. */
    private String tokenFor(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("roles", List.of("USER")).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> wrongLogin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"reader\",\"password\":\"wrong\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void apiRequestsAreLimitedPerUserWithAProblemResponse() throws Exception {
        String token = tokenFor("limited-user");
        HttpResponse<String> first = get(LATEST, token);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("X-RateLimit-Remaining")).hasValue("4");
        for (int i = 0; i < 4; i++) {
            assertThat(get(LATEST, token).statusCode()).isEqualTo(200);
        }

        HttpResponse<String> limited = get(LATEST, token);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(limited.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(limited.body()).contains("\"status\":429").contains("\"timestamp\"").contains("\"title\"");
    }

    @Test
    void eachUserHasAnIndependentBucket() throws Exception {
        String one = tokenFor("user-one");
        String two = tokenFor("user-two");
        for (int i = 0; i < 5; i++) {
            assertThat(get(LATEST, one).statusCode()).isEqualTo(200);
        }
        assertThat(get(LATEST, one).statusCode()).isEqualTo(429);
        assertThat(get(LATEST, two).statusCode()).isEqualTo(200);
    }

    @Test
    void loginAttemptsAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(wrongLogin().statusCode()).isEqualTo(401);
        }
        HttpResponse<String> limited = wrongLogin();
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void healthChecksAreNeverRateLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(200);
        }
    }
}
