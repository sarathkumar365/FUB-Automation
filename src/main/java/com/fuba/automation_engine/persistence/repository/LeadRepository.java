package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.LeadEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<LeadEntity, Long> {

    Optional<LeadEntity> findBySourceSystemAndSourceLeadId(String sourceSystem, String sourceLeadId);
}
