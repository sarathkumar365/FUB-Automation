package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PersonEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<PersonEntity, Long> {

    Optional<PersonEntity> findBySourceSystemAndSourcePersonId(String sourceSystem, String sourcePersonId);
}
