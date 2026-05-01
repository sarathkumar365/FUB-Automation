package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import com.fuba.automation_engine.persistence.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldPersistAndFindByUsernameIgnoreCase() {
        repository.saveAndFlush(buildUser("Admin", AppUserRole.ADMIN));

        Optional<AppUserEntity> lookup = repository.findByUsernameIgnoreCase("admin");

        assertTrue(lookup.isPresent());
        assertEquals(AppUserRole.ADMIN, lookup.get().getRole());
        assertTrue(lookup.get().isEnabled());
    }

    @Test
    void shouldReturnEmptyForUnknownUsername() {
        assertFalse(repository.findByUsernameIgnoreCase("ghost").isPresent());
    }

    @Test
    void shouldRejectDuplicateUsername() {
        repository.saveAndFlush(buildUser("dupuser", AppUserRole.OPERATOR));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(buildUser("dupuser", AppUserRole.VIEWER)));
    }

    @Test
    void shouldUpdateLastLoginAt() {
        AppUserEntity saved = repository.saveAndFlush(buildUser("op", AppUserRole.OPERATOR));
        OffsetDateTime ts = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        int updated = repository.touchLastLogin(saved.getId(), ts);
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, updated);
        AppUserEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertNotNull(reloaded.getLastLoginAt());
        assertEquals(ts.toInstant(), reloaded.getLastLoginAt().toInstant());
    }

    private AppUserEntity buildUser(String username, AppUserRole role) {
        OffsetDateTime now = OffsetDateTime.now();
        AppUserEntity entity = new AppUserEntity();
        entity.setUsername(username);
        entity.setPasswordHash("$2a$12$placeholderhashvalueforunittest");
        entity.setRole(role);
        entity.setEnabled(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
