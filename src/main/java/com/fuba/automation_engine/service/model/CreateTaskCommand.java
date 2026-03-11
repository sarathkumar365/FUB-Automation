package com.fuba.automation_engine.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreateTaskCommand(
        Long personId,
        String name,
        Long assignedUserId,
        LocalDate dueDate,
        OffsetDateTime dueDateTime) {
}

