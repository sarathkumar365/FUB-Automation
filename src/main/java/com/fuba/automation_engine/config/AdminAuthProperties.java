package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "admin.auth")
public class AdminAuthProperties {

    /**
     * Username used to seed the first admin user when the {@code app_user} table is empty.
     * Blank in {@code prod} fails startup; blank in {@code local} logs a warning.
     */
    private String seedUsername = "";

    /** Plaintext password for the seed user; bcrypt-hashed before insert. */
    private String seedPassword = "";
}
