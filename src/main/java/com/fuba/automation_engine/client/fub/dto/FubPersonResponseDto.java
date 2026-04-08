package com.fuba.automation_engine.client.fub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FubPersonResponseDto(
        Long id,
        Boolean claimed,
        Long assignedUserId,
        Integer contacted) {
}
