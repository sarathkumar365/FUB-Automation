package com.fuba.automation_engine.service.workflow;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Per-step execution context. Built fresh in
 * {@code WorkflowStepExecutionService.buildRunContext} before each step runs,
 * so any pre-resolved data ({@code person}, {@code now}, future {@code config})
 * reflects the latest state at the moment of step execution.
 *
 * <p>{@link com.fuba.automation_engine.service.workflow.expression.ExpressionScope}
 * is a pure mapper that exposes these fields under JSONata-friendly top-level
 * keys; data resolution lives in {@code buildRunContext}, not in the scope.
 *
 * @param metadata        run-identifying metadata
 * @param triggerPayload  the webhook payload that started the run
 * @param sourcePersonId  the FUB person ID this run operates on (may be null
 *                        for workflows triggered by non-person events)
 * @param person          the locally-snapshotted person details (Map view of
 *                        {@code persons.person_details} plus {@code kind}); empty
 *                        map when no snapshot exists or {@code sourcePersonId} is
 *                        null. See
 *                        {@link com.fuba.automation_engine.service.person.PersonSnapshotResolver}
 * @param now             time-of-day flags resolved at step execution from
 *                        {@code BusinessHoursService}; carries
 *                        {@code isDaytime} (boolean) and {@code hourLocal}
 *                        (int 0-23). Workflows reach these via
 *                        {@code {{ now.isDaytime }}} / {@code {{ now.hourLocal }}}.
 * @param stepOutputs     outputs produced by previously-completed steps in
 *                        this run, keyed by node id
 */
public record RunContext(
        RunMetadata metadata,
        Map<String, Object> triggerPayload,
        String sourcePersonId,
        Map<String, Object> person,
        Map<String, Object> now,
        Map<String, Map<String, Object>> stepOutputs) {

    public record RunMetadata(
            long runId,
            String workflowKey,
            long workflowVersion,
            OffsetDateTime runStartedAt,
            Long webhookEventId) {}
}
