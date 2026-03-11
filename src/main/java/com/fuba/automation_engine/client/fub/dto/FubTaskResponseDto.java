package com.fuba.automation_engine.client.fub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FubTaskResponseDto(
        Long id,
        Long personId,
        Long assignedUserId,
        String name,
        LocalDate dueDate,
        OffsetDateTime dueDateTime) {
}

