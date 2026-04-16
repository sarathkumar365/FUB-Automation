package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, Long> {

    Optional<WorkflowRunEntity> findByIdempotencyKey(String idempotencyKey);

    @Query(
            value = """
                    select r
                    from WorkflowRunEntity r
                    where r.workflowKey = :workflowKey
                      and (:status is null or r.status = :status)
                    """,
            countQuery = """
                    select count(r)
                    from WorkflowRunEntity r
                    where r.workflowKey = :workflowKey
                      and (:status is null or r.status = :status)
                    """)
    Page<WorkflowRunEntity> findByWorkflowKeyAndStatusFilter(
            @Param("workflowKey") String workflowKey,
            @Param("status") WorkflowRunStatus status,
            Pageable pageable);

    @Query(
            value = """
                    select r
                    from WorkflowRunEntity r
                    where (:status is null or r.status = :status)
                    """,
            countQuery = """
                    select count(r)
                    from WorkflowRunEntity r
                    where (:status is null or r.status = :status)
                    """)
    Page<WorkflowRunEntity> findByStatusFilter(
            @Param("status") WorkflowRunStatus status,
            Pageable pageable);
}
