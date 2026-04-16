package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunStepRepository extends JpaRepository<WorkflowRunStepEntity, Long> {

    List<WorkflowRunStepEntity> findByRunId(Long runId);

    List<WorkflowRunStepEntity> findByRunIdAndNodeIdIn(Long runId, Collection<String> nodeIds);

    Optional<WorkflowRunStepEntity> findByRunIdAndNodeId(Long runId, String nodeId);
}
