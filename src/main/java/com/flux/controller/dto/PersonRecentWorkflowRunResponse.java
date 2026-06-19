package com.flux.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flux.persistence.entity.WorkflowRunStatus;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonRecentWorkflowRunResponse(
        Long id,
        String workflowKey,
        Long workflowVersion,
        WorkflowRunStatus status,
        String reasonCode,
        OffsetDateTime createdAt) {
}
