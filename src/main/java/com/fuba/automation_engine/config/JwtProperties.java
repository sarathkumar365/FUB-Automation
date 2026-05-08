package com.fuba.automation_engine.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "admin.auth.jwt")
public class JwtProperties {

    /**
     * HMAC-SHA256 signing secret. Must be at least 32 characters (256 bits) to satisfy
     * the HS256 minimum. Blank in local development triggers an ephemeral random key
     * with a warning; blank in deployed environments fails startup (see {@link
     * com.fuba.automation_engine.service.auth.JwtKeyFactory}).
     */
    private String secret = "";

    /** Issuer claim ("iss") set on issued tokens and required on parse. */
    private String issuer = "automation-engine";

    /** Token lifetime from issuance. */
    private Duration expiry = Duration.ofHours(8);
}
