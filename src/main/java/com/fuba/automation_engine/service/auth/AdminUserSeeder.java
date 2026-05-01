package com.fuba.automation_engine.service.auth;

import com.fuba.automation_engine.config.AdminAuthProperties;
import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import com.fuba.automation_engine.persistence.repository.AppUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * One-shot first-boot seeder for the {@code app_user} table.
 *
 * <p>If the table is empty and {@code admin.auth.seed-username} / {@code admin.auth.seed-password}
 * are non-blank, inserts a single {@link AppUserRole#ADMIN} user. Subsequent password changes go
 * through SQL or a future user-management endpoint — env-var seeding is one-shot.
 *
 * <p>If the table is empty AND the seed credentials are blank, behaviour depends on the active
 * profile: in {@code prod} startup fails with a clear message; in {@code local} a {@code WARN}
 * is logged so dev-loop ergonomics aren't ruined for fresh checkouts.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);
    private static final String LOCAL_PROFILE = "local";

    private final AppUserRepository repository;
    private final AdminAuthProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final Clock clock;

    public AdminUserSeeder(
            AppUserRepository repository,
            AdminAuthProperties properties,
            PasswordEncoder passwordEncoder,
            Environment environment,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.debug("AdminUserSeeder: app_user table already populated; skipping seed.");
            return;
        }
        String username = properties.getSeedUsername();
        String password = properties.getSeedPassword();
        if (isBlank(username) || isBlank(password)) {
            handleMissingCredentials();
            return;
        }
        AppUserEntity user = newAdmin(username.trim(), password);
        repository.save(user);
        log.info("AdminUserSeeder: inserted ADMIN user username={}", user.getUsername());
    }

    private void handleMissingCredentials() {
        if (isLocalProfile()) {
            log.warn("AdminUserSeeder: app_user table empty and ADMIN_AUTH_USERNAME/ADMIN_AUTH_PASSWORD "
                    + "not set; no admin user seeded (local profile).");
            return;
        }
        throw new IllegalStateException(
                "app_user table is empty and admin.auth.seed-username/seed-password are not set; "
                        + "set ADMIN_AUTH_USERNAME and ADMIN_AUTH_PASSWORD before first start.");
    }

    private AppUserEntity newAdmin(String username, String plaintextPassword) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        AppUserEntity entity = new AppUserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(plaintextPassword));
        entity.setRole(AppUserRole.ADMIN);
        entity.setEnabled(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private boolean isLocalProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (LOCAL_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return environment.getActiveProfiles().length == 0;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
