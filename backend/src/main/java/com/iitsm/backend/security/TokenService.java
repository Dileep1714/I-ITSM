package com.iitsm.backend.security;

import com.iitsm.backend.config.ItsmProperties;
import com.iitsm.backend.domain.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Issues signed login tokens (JWT).
 *
 * <p>A token states who the user is ({@code sub} = user id) and their role, and is signed with the server's secret
 * key, so it cannot be forged or edited. The server keeps no sessions: each request carries its token. That is
 * what makes the API <em>stateless</em>, and lets it scale to several server instances without shared session
 * storage.
 */
@Service
public class TokenService {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final ItsmProperties.Security properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, ItsmProperties.Security properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenLifetime());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim("name", user.getFullName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
