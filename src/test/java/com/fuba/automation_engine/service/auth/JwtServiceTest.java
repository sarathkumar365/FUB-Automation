package com.fuba.automation_engine.service.auth;

import com.fuba.automation_engine.config.JwtProperties;
import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final Instant FIXED = Instant.parse("2026-05-01T12:00:00Z");

    @Test
    void issueAndParseRoundTripPreservesClaims() {
        JwtService service = service(FIXED);

        JwtService.IssuedToken issued = service.issue(user("alice", AppUserRole.ADMIN));
        JwtPrincipal principal = service.parse(issued.token());

        assertEquals("alice", principal.username());
        assertEquals(AppUserRole.ADMIN, principal.role());
        assertEquals(FIXED.plus(Duration.ofHours(8)), issued.expiresAt());
    }

    @Test
    void parseRejectsTokenAfterExpiry() {
        JwtService issuer = service(FIXED);
        String token = issuer.issue(user("bob", AppUserRole.OPERATOR)).token();

        JwtService later = service(FIXED.plus(Duration.ofHours(9)));

        assertThrows(JwtException.class, () -> later.parse(token));
    }

    @Test
    void parseRejectsTokenWithTamperedSignature() {
        JwtService service = service(FIXED);
        String token = service.issue(user("carol", AppUserRole.VIEWER)).token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(JwtException.class, () -> service.parse(tampered));
    }

    @Test
    void parseRejectsTokenIssuedByDifferentSecret() {
        JwtService service = service(FIXED);
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "ffffffffffffffffffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .issuer("automation-engine")
                .subject("attacker")
                .claim(JwtService.ROLE_CLAIM, "ADMIN")
                .issuedAt(Date.from(FIXED))
                .expiration(Date.from(FIXED.plus(Duration.ofMinutes(5))))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThrows(JwtException.class, () -> service.parse(foreignToken));
    }

    @Test
    void parseRejectsTokenWithWrongIssuer() {
        JwtService service = service(FIXED);
        String foreignIssuer = Jwts.builder()
                .issuer("evil-issuer")
                .subject("dave")
                .claim(JwtService.ROLE_CLAIM, "ADMIN")
                .issuedAt(Date.from(FIXED))
                .expiration(Date.from(FIXED.plus(Duration.ofMinutes(5))))
                .signWith(KEY, Jwts.SIG.HS256)
                .compact();

        assertThrows(JwtException.class, () -> service.parse(foreignIssuer));
    }

    @Test
    void parseRejectsTokenWithUnknownRoleClaim() {
        JwtService service = service(FIXED);
        String badRole = Jwts.builder()
                .issuer("automation-engine")
                .subject("eve")
                .claim(JwtService.ROLE_CLAIM, "SUPERUSER")
                .issuedAt(Date.from(FIXED))
                .expiration(Date.from(FIXED.plus(Duration.ofMinutes(5))))
                .signWith(KEY, Jwts.SIG.HS256)
                .compact();

        JwtException ex = assertThrows(JwtException.class, () -> service.parse(badRole));
        assertTrue(ex.getMessage().contains("SUPERUSER"));
    }

    private JwtService service(Instant now) {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setIssuer("automation-engine");
        props.setExpiry(Duration.ofHours(8));
        return new JwtService(KEY, props, Clock.fixed(now, ZoneOffset.UTC));
    }

    private AppUserEntity user(String username, AppUserRole role) {
        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
