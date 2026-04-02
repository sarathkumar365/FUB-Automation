package com.fuba.automation_engine.controller.dto;

import com.fuba.automation_engine.persistence.entity.PolicyStatus;

public record PolicyResponse(
        Long id,
        String domain,
        String policyKey,
        boolean enabled,
        int dueAfterMinutes,
        PolicyStatus status,
        Long version) {
}
