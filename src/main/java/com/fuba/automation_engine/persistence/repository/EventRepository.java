package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventEntity, Long> {
}
