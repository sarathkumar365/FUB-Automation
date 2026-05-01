package com.fuba.automation_engine.controller.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record WorkflowRunStepDetail(
        Long id,
        String nodeId,
        String stepType,
        String status,
        String resultCode,
        Map<String, Object> outputs,
        String errorMessage,
        Integer retryCount,
        OffsetDateTime dueAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt) {
}
