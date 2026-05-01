package com.fuba.automation_engine.service.auth;

import com.fuba.automation_engine.config.AdminAuthProperties;
import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import com.fuba.automation_engine.persistence.repository.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserSeederTest {

    private AppUserRepository repository;
    private PasswordEncoder passwordEncoder;
    private AdminAuthProperties properties;
    private MockEnvironment environment;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repository = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        properties = new AdminAuthProperties();
        environment = new MockEnvironment();
        clock = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encoded");
    }

    @Test
    void seedsAdminWhenTableEmptyAndCredentialsProvided() {
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(AppUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        properties.setSeedUsername("admin");
        properties.setSeedPassword("devpass");
        environment.setActiveProfiles("local");

        seeder().run(null);

        verify(passwordEncoder, atLeastOnce()).encode("devpass");
        verify(repository, times(1)).save(any(AppUserEntity.class));
    }

    @Test
    void skipsWhenTablePopulated() {
        when(repository.count()).thenReturn(1L);
        properties.setSeedUsername("admin");
        properties.setSeedPassword("devpass");

        seeder().run(null);

        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void warnsAndSkipsWhenLocalProfileWithoutCredentials() {
        when(repository.count()).thenReturn(0L);
        environment.setActiveProfiles("local");

        // Should not throw.
        seeder().run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void warnsAndSkipsWhenNoActiveProfileWithoutCredentials() {
        when(repository.count()).thenReturn(0L);
        // Default-no-profiles equivalent to local for safety reasons in tests.

        seeder().run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void failsFastWhenProdProfileWithoutCredentials() {
        when(repository.count()).thenReturn(0L);
        environment.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> seeder().run(null));
        assertTrue(ex.getMessage().contains("ADMIN_AUTH_USERNAME"));
    }

    private AdminUserSeeder seeder() {
        return new AdminUserSeeder(repository, properties, passwordEncoder, environment, clock);
    }
}
