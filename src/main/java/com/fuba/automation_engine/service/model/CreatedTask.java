package com.fuba.automation_engine.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreatedTask(
        Long id,
        Long personId,
        Long assignedUserId,
        String name,
        LocalDate dueDate,
        OffsetDateTime dueDateTime) {
}

