package com.bhavin.currencyapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Requests allowed per minute. Zero or missing values fall back to the defaults. */
@ConfigurationProperties(prefix = "app.ratelimit")
public record RateLimitProperties(int loginPerMinute, int apiPerMinute) {

    public RateLimitProperties {
        if (loginPerMinute <= 0) {
            loginPerMinute = 5;
        }
        if (apiPerMinute <= 0) {
            apiPerMinute = 60;
        }
    }
}
