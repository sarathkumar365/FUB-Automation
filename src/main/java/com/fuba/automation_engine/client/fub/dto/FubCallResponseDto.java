package com.fuba.automation_engine.client.fub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FubCallResponseDto(
        Long id,
        Long personId,
        Integer duration,
        Long userId,
        String outcome,
        Boolean isIncoming,
        OffsetDateTime created) {
}
