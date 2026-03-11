package com.fuba.automation_engine.client.fub.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FubCreateTaskRequestDto(
        Long personId,
        String name,
        Long assignedUserId,
        LocalDate dueDate,
        OffsetDateTime dueDateTime) {
}

