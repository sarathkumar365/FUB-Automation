package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProcessedCallRepository
        extends JpaRepository<ProcessedCallEntity, Long>, JpaSpecificationExecutor<ProcessedCallEntity> {

    Optional<ProcessedCallEntity> findByCallId(Long callId);
}
