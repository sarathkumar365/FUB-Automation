package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyExecutionRunRepository extends JpaRepository<PolicyExecutionRunEntity, Long> {

    Optional<PolicyExecutionRunEntity> findByIdempotencyKey(String idempotencyKey);
}
