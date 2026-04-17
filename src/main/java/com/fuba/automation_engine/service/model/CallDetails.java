package com.fuba.automation_engine.service.model;

import java.time.OffsetDateTime;

public record CallDetails(
        Long id,
        Long personId,
        Integer duration,
        Long userId,
        String outcome,
        Boolean isIncoming,
        OffsetDateTime createdAt) {

    public CallDetails(Long id, Long personId, Integer duration, Long userId, String outcome) {
        this(id, personId, duration, userId, outcome, null, null);
    }
}
