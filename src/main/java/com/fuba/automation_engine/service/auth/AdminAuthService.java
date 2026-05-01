package com.fuba.automation_engine.service.auth;

import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.repository.AppUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encapsulates the side effects that happen on a successful login (last-login bookkeeping)
 * so the controller stays thin. Future audit-logging hooks land here.
 */
@Service
public class AdminAuthService {

    private final AppUserRepository repository;
    private final Clock clock;

    public AdminAuthService(AppUserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Optional<AppUserEntity> findActiveByUsername(String username) {
        return repository.findByUsernameIgnoreCase(username)
                .filter(AppUserEntity::isEnabled);
    }

    @Transactional
    public void recordLogin(Long userId) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        repository.touchLastLogin(userId, now);
    }
}
