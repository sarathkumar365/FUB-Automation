package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationWorkflowRepository extends JpaRepository<AutomationWorkflowEntity, Long> {

    Optional<AutomationWorkflowEntity> findFirstByKeyAndStatusOrderByIdDesc(String key, WorkflowStatus status);

    List<AutomationWorkflowEntity> findByKeyOrderByIdDesc(String key);

    List<AutomationWorkflowEntity> findByStatus(WorkflowStatus status);
}
