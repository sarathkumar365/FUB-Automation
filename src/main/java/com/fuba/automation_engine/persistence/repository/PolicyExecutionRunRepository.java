package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyExecutionRunRepository extends JpaRepository<PolicyExecutionRunEntity, Long> {

    Optional<PolicyExecutionRunEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select run
            from PolicyExecutionRunEntity run
            where (:status is null or run.status = :status)
              and (:policyKey is null or run.policyKey = :policyKey)
              and (:from is null or run.createdAt >= :from)
              and (:to is null or run.createdAt <= :to)
              and (
                    :cursorCreatedAt is null
                    or run.createdAt < :cursorCreatedAt
                    or (run.createdAt = :cursorCreatedAt and run.id < :cursorId)
              )
            order by run.createdAt desc, run.id desc
            """)
    List<PolicyExecutionRunEntity> findForAdminFeed(
            @Param("status") PolicyExecutionRunStatus status,
            @Param("policyKey") String policyKey,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
