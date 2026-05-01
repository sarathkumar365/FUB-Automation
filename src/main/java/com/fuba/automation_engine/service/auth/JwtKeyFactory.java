package com.fuba.automation_engine.service.auth;

import com.fuba.automation_engine.config.JwtProperties;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Builds the HS256 signing key from {@link JwtProperties}. Encapsulates the policy:
 * - blank secret in deployed (non-local) profiles → fail startup,
 * - blank secret in local development → ephemeral random key with a loud warning,
 * - non-blank secret shorter than 32 chars (256 bits) → fail startup unconditionally.
 */
@Configuration
public class JwtKeyFactory {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyFactory.class);
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String LOCAL_PROFILE = "local";

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties, Environment environment) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            return handleBlankSecret(environment);
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "admin.auth.jwt.secret (env JWT_SECRET) must be at least "
                            + MIN_SECRET_LENGTH
                            + " characters; got "
                            + secret.length());
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey handleBlankSecret(Environment environment) {
        boolean local = isLocalProfile(environment);
        if (!local) {
            throw new IllegalStateException(
                    "admin.auth.jwt.secret (env JWT_SECRET) must be set when running outside the 'local' profile");
        }
        byte[] random = new byte[64];
        new SecureRandom().nextBytes(random);
        log.warn("JWT_SECRET is blank; using an ephemeral random key (local profile only). "
                + "Tokens will not survive a restart. Set JWT_SECRET to silence this.");
        return Keys.hmacShaKeyFor(random);
    }

    private boolean isLocalProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (LOCAL_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        if (environment.getActiveProfiles().length == 0) {
            for (String profile : environment.getDefaultProfiles()) {
                if (LOCAL_PROFILE.equalsIgnoreCase(profile)) {
                    return true;
                }
            }
        }
        return false;
    }
}
