package com.bhavin.currencyapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bhavin.currencyapi.client.RateProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void upstreamFailureBecomesA502Problem() {
        ProblemDetail problem = handler.handleUpstream(mock(RateProviderException.class));

        assertThat(problem.getStatus()).isEqualTo(502);
        assertThat(problem.getTitle()).isEqualTo("Upstream provider error");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void unexpectedErrorBecomesA500ThatDoesNotLeakTheMessage() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("db password is hunter2"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("hunter2");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }
}
