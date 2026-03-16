package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCallRepository extends JpaRepository<ProcessedCallEntity, Long> {

    Optional<ProcessedCallEntity> findByCallId(Long callId);
}
