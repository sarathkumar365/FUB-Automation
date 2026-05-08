package com.fuba.automation_engine.service.auth;

import com.fuba.automation_engine.config.JwtProperties;
import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and validates JWTs used for `/admin/**` bearer authentication. */
@Service
public class JwtService {

    static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(SecretKey jwtSigningKey, JwtProperties properties, Clock clock) {
        this.signingKey = jwtSigningKey;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(AppUserEntity user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getExpiry());
        String token = Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUsername())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public JwtPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            throw new JwtException("token missing subject claim");
        }
        Object roleClaim = claims.get(ROLE_CLAIM);
        if (!(roleClaim instanceof String roleString) || roleString.isBlank()) {
            throw new JwtException("token missing role claim");
        }
        AppUserRole role;
        try {
            role = AppUserRole.valueOf(roleString);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("token has unknown role claim: " + roleString, ex);
        }
        return new JwtPrincipal(username, role);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
