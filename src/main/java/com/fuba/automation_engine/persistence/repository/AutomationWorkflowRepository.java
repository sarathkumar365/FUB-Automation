package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationWorkflowRepository extends JpaRepository<AutomationWorkflowEntity, Long> {

    Optional<AutomationWorkflowEntity> findFirstByKeyAndStatusOrderByIdDesc(String key, WorkflowStatus status);

    Optional<AutomationWorkflowEntity> findFirstByKeyOrderByVersionNumberDesc(String key);

    List<AutomationWorkflowEntity> findByKeyOrderByVersionNumberDesc(String key);

    Optional<AutomationWorkflowEntity> findByKeyAndVersionNumber(String key, Integer versionNumber);

    @Query("select max(w.versionNumber) from AutomationWorkflowEntity w where w.key = :key")
    Optional<Integer> findMaxVersionNumberByKey(@Param("key") String key);

    @Query(
            value = """
                    select w
                    from AutomationWorkflowEntity w
                    where w.versionNumber = (
                        select max(w2.versionNumber)
                        from AutomationWorkflowEntity w2
                        where w2.key = w.key
                    )
                    and (
                        (:status is null and w.status <> :archivedStatus)
                        or (:status is not null and w.status = :status)
                    )
                    """,
            countQuery = """
                    select count(w)
                    from AutomationWorkflowEntity w
                    where w.versionNumber = (
                        select max(w2.versionNumber)
                        from AutomationWorkflowEntity w2
                        where w2.key = w.key
                    )
                    and (
                        (:status is null and w.status <> :archivedStatus)
                        or (:status is not null and w.status = :status)
                    )
                    """)
    Page<AutomationWorkflowEntity> findLatestByStatusFilter(
            @Param("status") WorkflowStatus status,
            @Param("archivedStatus") WorkflowStatus archivedStatus,
            Pageable pageable);

    List<AutomationWorkflowEntity> findByStatus(WorkflowStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AutomationWorkflowEntity workflow
            set workflow.status = :inactiveStatus
            where workflow.key = :key
              and workflow.status = :activeStatus
              and workflow.id <> :excludedId
            """)
    int deactivateActiveWorkflowsByKeyExcludingId(
            @Param("key") String key,
            @Param("excludedId") Long excludedId,
            @Param("activeStatus") WorkflowStatus activeStatus,
            @Param("inactiveStatus") WorkflowStatus inactiveStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AutomationWorkflowEntity workflow
            set workflow.status = :inactiveStatus
            where workflow.key = :key
              and workflow.status = :activeStatus
            """)
    int deactivateActiveWorkflowsByKey(
            @Param("key") String key,
            @Param("activeStatus") WorkflowStatus activeStatus,
            @Param("inactiveStatus") WorkflowStatus inactiveStatus);
}
