package com.bhavin.currencyapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private static SecretKeySpec key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static JwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AppSecurityProperties props =
            new AppSecurityProperties(SECRET, "reader-pw", "admin-pw", Duration.ofMinutes(5));
    private final TokenService service = new TokenService(
            new InMemoryUserDetailsManager(
                    User.withUsername("admin").password(passwordEncoder.encode("admin-pw")).roles("USER", "ADMIN").build()),
            passwordEncoder,
            new NimbusJwtEncoder(new ImmutableSecret<>(key(SECRET))),
            props);

    @Test
    void correctCredentialsIssueASignedTokenWithSubjectRolesAndExpiry() {
        Optional<TokenService.IssuedToken> issued = service.issue("admin", "admin-pw");

        assertThat(issued).isPresent();
        assertThat(issued.get().expiresInSeconds()).isEqualTo(300);
        Jwt jwt = decoder(SECRET).decode(issued.get().value());
        assertThat(jwt.getSubject()).isEqualTo("admin");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(301));
    }

    @Test
    void wrongPasswordIssuesNothing() {
        assertThat(service.issue("admin", "nope")).isEmpty();
    }

    @Test
    void unknownUserIssuesNothing() {
        assertThat(service.issue("nobody", "admin-pw")).isEmpty();
    }

    @Test
    void aTokenSignedWithADifferentSecretIsRejected() {
        String token = service.issue("admin", "admin-pw").orElseThrow().value();

        assertThatThrownBy(() -> decoder("a-completely-different-secret-0123456789").decode(token))
                .isInstanceOf(JwtException.class);
    }
}
