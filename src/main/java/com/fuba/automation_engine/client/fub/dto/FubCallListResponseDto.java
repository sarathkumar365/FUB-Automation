package com.fuba.automation_engine.client.fub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FubCallListResponseDto(List<FubCallResponseDto> calls) {
}
