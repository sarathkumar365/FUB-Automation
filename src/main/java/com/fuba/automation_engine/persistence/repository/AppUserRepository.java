package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    @Query("select u from AppUserEntity u where lower(u.username) = lower(:username)")
    Optional<AppUserEntity> findByUsernameIgnoreCase(@Param("username") String username);

    @Modifying
    @Query("update AppUserEntity u set u.lastLoginAt = :ts where u.id = :id")
    int touchLastLogin(@Param("id") Long id, @Param("ts") OffsetDateTime ts);
}
