package com.bhavin.currencyapi.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    public record IssuedToken(String value, long expiresInSeconds) {
    }

    private final UserDetailsService users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppSecurityProperties props;
    private final String dummyHash;

    public TokenService(UserDetailsService users, PasswordEncoder encoder, JwtEncoder jwtEncoder,
                        AppSecurityProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /** Empty when the credentials are wrong. Unknown users cost the same BCrypt time as wrong passwords. */
    public Optional<IssuedToken> issue(String username, String password) {
        UserDetails user;
        try {
            user = users.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            encoder.matches(password, dummyHash);
            return Optional.empty();
        }
        if (!encoder.matches(password, user.getPassword())) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("currency-price-api")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtl()))
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return Optional.of(new IssuedToken(token, props.tokenTtl().toSeconds()));
    }
}
