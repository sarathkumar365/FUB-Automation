package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadRecentWorkflowRunResponse(
        Long id,
        String workflowKey,
        Long workflowVersion,
        WorkflowRunStatus status,
        String reasonCode,
        OffsetDateTime createdAt) {
}
