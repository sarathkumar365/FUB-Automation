package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedCallRepository extends JpaRepository<ProcessedCallEntity, Long> {

    Optional<ProcessedCallEntity> findByCallId(Long callId);

    @Query("""
            select p from ProcessedCallEntity p
            where (:status is null or p.status = :status)
              and (:from is null or p.updatedAt >= :from)
              and (:to is null or p.updatedAt <= :to)
            order by p.updatedAt desc
            """)
    List<ProcessedCallEntity> findForAdmin(
            @Param("status") ProcessedCallStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
