package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PersonEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<PersonEntity, Long> {

    Optional<PersonEntity> findBySourceSystemAndSourcePersonId(String sourceSystem, String sourcePersonId);

    /**
     * Pessimistic-write variant of the source-id finder. Used by
     * {@code PersonUpsertService.upsertFubPerson} to serialize concurrent
     * upserts of the same person — see
     * {@code Docs/features/domain-events/plan.md} §"Per-person serialization
     * of the upsert".
     *
     * <p>Why an explicit {@code @Query} instead of a derived method name:
     * Spring Data is finicky about applying {@code @Lock} to plain derived
     * finders, especially across Hibernate / Spring Data version bumps.
     * Explicit JPQL pins the query shape so the lock annotation is honoured
     * without surprises.
     *
     * <p>Must be used at <b>both</b> upsert read sites — the primary finder
     * and the {@code DataIntegrityViolationException} recovery re-read. The
     * recovery path is the only path that runs when N parallel inserts race
     * for a brand-new row; if it doesn't lock, the per-person collapse claim
     * silently fails for brand-new persons.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PersonEntity p where p.sourceSystem = :sourceSystem and p.sourcePersonId = :sourcePersonId")
    Optional<PersonEntity> findBySourceSystemAndSourcePersonIdForUpdate(
            @Param("sourceSystem") String sourceSystem,
            @Param("sourcePersonId") String sourcePersonId);
}
