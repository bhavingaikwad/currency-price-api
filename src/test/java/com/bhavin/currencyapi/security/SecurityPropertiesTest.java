package com.bhavin.currencyapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

    private static final String GOOD_SECRET = "unit-test-secret-unit-test-secret-1234";

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new AppSecurityProperties("too-short", "r", "a", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void missingSecretIsRejected() {
        assertThatThrownBy(() -> new AppSecurityProperties(null, "r", "a", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankPasswordsAreRejected() {
        assertThatThrownBy(() -> new AppSecurityProperties(GOOD_SECRET, " ", "a", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppSecurityProperties(GOOD_SECRET, "r", null, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenTtlDefaultsToThirtyMinutes() {
        assertThat(new AppSecurityProperties(GOOD_SECRET, "r", "a", null).tokenTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rateLimitsFallBackToDefaultsWhenUnset() {
        RateLimitProperties defaults = new RateLimitProperties(0, 0);
        assertThat(defaults.loginPerMinute()).isEqualTo(5);
        assertThat(defaults.apiPerMinute()).isEqualTo(60);
        assertThat(new RateLimitProperties(2, 9).loginPerMinute()).isEqualTo(2);
    }
}
