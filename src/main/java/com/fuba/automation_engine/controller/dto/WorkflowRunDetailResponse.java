package com.fuba.automation_engine.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WorkflowRunDetailResponse(
        Long id,
        String workflowKey,
        Long workflowVersionNumber,
        String status,
        String reasonCode,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        Map<String, Object> triggerPayload,
        String sourceLeadId,
        String eventId,
        List<WorkflowRunStepDetail> steps) {
}
