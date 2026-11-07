package com.bhavin.currencyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateApiErrorTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Value("${local.server.port}")
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }

    private String login(String username, String password) throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/auth/token", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.body()).contains("\"status\":" + status).contains("\"timestamp\"").contains("\"title\"");
    }

    @Test
    void noTokenIsUnauthorizedProblem() throws Exception {
        HttpResponse<String> response = get("/api/v1/rates/latest?provider=BINANCE", null);
        assertProblem(response, 401);
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse("")).contains("Bearer");
    }

    @Test
    void garbageTokenIsUnauthorizedProblem() throws Exception {
        assertProblem(get("/api/v1/rates/latest?provider=BINANCE", "not.a.jwt"), 401);
    }

    @Test
    void wrongPasswordIsUnauthorizedProblem() throws Exception {
        assertProblem(send("POST", "/api/v1/auth/token", null, "{\"username\":\"reader\",\"password\":\"wrong\"}"), 401);
    }

    @Test
    void unknownUserIsUnauthorizedProblem() throws Exception {
        assertProblem(send("POST", "/api/v1/auth/token", null, "{\"username\":\"nobody\",\"password\":\"x\"}"), 401);
    }

    @Test
    void blankUsernameIsABadRequestProblem() throws Exception {
        assertProblem(send("POST", "/api/v1/auth/token", null, "{\"username\":\"\",\"password\":\"x\"}"), 400);
    }

    @Test
    void unknownProviderIsABadRequestProblem() throws Exception {
        assertProblem(get("/api/v1/rates/latest?provider=NOPE", login("reader", "reader-pw")), 400);
    }

    @Test
    void missingProviderIsABadRequestProblem() throws Exception {
        assertProblem(get("/api/v1/rates/latest", login("reader", "reader-pw")), 400);
    }

    @Test
    void unknownRouteIsANotFoundProblem() throws Exception {
        assertProblem(get("/api/v1/does-not-exist", login("reader", "reader-pw")), 404);
    }

    @Test
    void validProviderOnEmptyDatabaseIsAnEmptyList() throws Exception {
        HttpResponse<String> response = get("/api/v1/rates/latest?provider=BINANCE", login("reader", "reader-pw"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    void healthEndpointStaysOpenWithoutAToken() throws Exception {
        assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(200);
    }

    @Test
    void readerIsForbiddenFromMetricsButAdminIsAllowed() throws Exception {
        assertProblem(get("/actuator/metrics", login("reader", "reader-pw")), 403);
        assertThat(get("/actuator/metrics", login("admin", "admin-pw")).statusCode()).isEqualTo(200);
    }
}
