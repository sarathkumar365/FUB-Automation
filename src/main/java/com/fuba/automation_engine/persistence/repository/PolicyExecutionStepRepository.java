package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyExecutionStepRepository extends JpaRepository<PolicyExecutionStepEntity, Long> {

    List<PolicyExecutionStepEntity> findByRunIdOrderByStepOrderAsc(Long runId);

    List<PolicyExecutionStepEntity> findByStatusAndDueAtLessThanEqualOrderByDueAtAscIdAsc(
            PolicyExecutionStepStatus status,
            OffsetDateTime dueAt);
}
