package com.fuba.automation_engine.client.fub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response shape for {@code POST /v1/notes}. Verified via smoke tests on 2026-05-06. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FubNoteResponseDto(
        Long id,
        Long personId,
        String subject,
        String body,
        Boolean isHtml) {
}
