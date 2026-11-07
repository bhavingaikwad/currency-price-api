package com.bhavin.currencyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bhavin.currencyapi.domain.Rate;
import com.bhavin.currencyapi.service.RateIngestService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LatestCachedIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    private static final String KEY = "latest:BINANCE";
    private static final String PATH = "/api/v1/rates/latest-cached?provider=BINANCE";

    @Value("${local.server.port}")
    int port;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    RateIngestService ingest;

    @Autowired
    MeterRegistry metrics;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String token;

    @BeforeEach
    void seedAndReset() throws Exception {
        ingest.ingestIfAbsent(new Rate(
                "BTC", "USDT", Instant.parse("2026-06-30T23:59:00Z"), new BigDecimal("100.5"), "BINANCE"));
        redisTemplate.delete(KEY);
        token = login();
    }

    private String login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"reader\",\"password\":\"reader-pw\"}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> get() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private double counter(String result) {
        return metrics.counter("rates.cache", "result", result).count();
    }

    @Test
    @Order(1)
    void missThenHitServesIdenticalBodiesAndStoresAKeyWithTtl() throws Exception {
        double hitsBefore = counter("hit");

        HttpResponse<String> first = get();
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"source\":\"BTC\"");
        assertThat(redisTemplate.getExpire(KEY)).isBetween(1L, 30L);

        HttpResponse<String> second = get();
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(counter("hit") - hitsBefore).isEqualTo(1.0);
    }

    @Test
    @Order(2)
    void entryWithAWrongWatermarkIsStaleAndReplacedFromTheDatabase() throws Exception {
        double staleBefore = counter("stale");
        redisTemplate.opsForValue().set(KEY,
                "{\"watermark\":-5,\"rates\":[{\"source\":\"FAKE\",\"target\":\"USDT\",\"value\":1,"
                        + "\"rateTime\":\"2026-01-01T00:00:00Z\",\"provider\":\"BINANCE\"}]}");

        HttpResponse<String> response = get();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"source\":\"BTC\"").doesNotContain("FAKE");
        assertThat(counter("stale") - staleBefore).isEqualTo(1.0);
        assertThat(redisTemplate.opsForValue().get(KEY)).doesNotContain("FAKE");
    }

    @Test
    @Order(3)
    void frozenRedisStillAnswers200FromTheDatabase() throws Exception {
        double errorsBefore = counter("error");
        var docker = redis.getDockerClient();
        docker.pauseContainerCmd(redis.getContainerId()).exec();
        try {
            long start = System.nanoTime();
            HttpResponse<String> response = get();
            long millis = (System.nanoTime() - start) / 1_000_000;

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"source\":\"BTC\"");
            assertThat(millis).isLessThan(3000);
            assertThat(counter("error")).isGreaterThan(errorsBefore);
        } finally {
            docker.unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }
}
