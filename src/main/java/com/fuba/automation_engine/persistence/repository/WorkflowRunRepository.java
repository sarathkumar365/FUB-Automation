package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, Long> {

    Optional<WorkflowRunEntity> findByIdempotencyKey(String idempotencyKey);
}
