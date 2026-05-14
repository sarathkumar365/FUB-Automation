package com.fuba.automation_engine.service.model;

/** Result of a successful {@code POST /v1/notes}. */
public record CreatedNote(
        Long id,
        Long personId,
        String subject,
        String body) {
}
