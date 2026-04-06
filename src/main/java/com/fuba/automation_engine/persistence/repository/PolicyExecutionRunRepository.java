package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PolicyExecutionRunRepository
        extends JpaRepository<PolicyExecutionRunEntity, Long>, JpaSpecificationExecutor<PolicyExecutionRunEntity> {

    Optional<PolicyExecutionRunEntity> findByIdempotencyKey(String idempotencyKey);
}
