package com.bhavin.currencyapi.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiting. Runs right after bearer-token authentication so it can key on the user.
 * Buckets are in memory (per instance) in a bounded, expiring cache.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/token";

    private record Rule(String key, int perMinute) {
    }

    private final RateLimitProperties limits;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public RateLimitFilter(RateLimitProperties limits) {
        this.limits = limits;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = ruleFor(request);
        Bucket bucket = buckets.get(rule.key(), key -> newBucket(rule.perMinute()));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ProblemSecurityHandlers.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
    }

    private Rule ruleFor(HttpServletRequest request) {
        if (LOGIN_PATH.equals(request.getRequestURI())) {
            return new Rule("login:" + request.getRemoteAddr(), limits.loginPerMinute());
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return new Rule("user:" + auth.getName(), limits.apiPerMinute());
        }
        return new Rule("ip:" + request.getRemoteAddr(), limits.apiPerMinute());
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
