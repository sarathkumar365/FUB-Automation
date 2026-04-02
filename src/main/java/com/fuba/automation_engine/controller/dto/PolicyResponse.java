package com.fuba.automation_engine.controller.dto;

import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import java.util.Map;

public record PolicyResponse(
        Long id,
        String domain,
        String policyKey,
        boolean enabled,
        Map<String, Object> blueprint,
        PolicyStatus status,
        Long version) {
}
