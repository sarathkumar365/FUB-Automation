package com.fuba.automation_engine.service.workflow;

import java.util.Map;

/**
 * Per-step execution context. Built fresh in
 * {@code WorkflowStepExecutionService.buildRunContext} before each step runs,
 * so any pre-resolved data ({@code lead}, future {@code now}, future
 * {@code config}) reflects the latest state at the moment of step execution.
 *
 * <p>{@link com.fuba.automation_engine.service.workflow.expression.ExpressionScope}
 * is a pure mapper that exposes these fields under JSONata-friendly top-level
 * keys; data resolution lives in {@code buildRunContext}, not in the scope.
 *
 * @param metadata        run-identifying metadata
 * @param triggerPayload  the webhook payload that started the run
 * @param sourceLeadId    the FUB person ID this run operates on (may be null
 *                        for workflows triggered by non-lead events)
 * @param lead            the locally-snapshotted lead details (Map view of
 *                        {@code leads.lead_details}); empty map when no
 *                        snapshot exists or {@code sourceLeadId} is null. See
 *                        {@link com.fuba.automation_engine.service.lead.LeadSnapshotResolver}
 * @param stepOutputs     outputs produced by previously-completed steps in
 *                        this run, keyed by node id
 */
public record RunContext(
        RunMetadata metadata,
        Map<String, Object> triggerPayload,
        String sourceLeadId,
        Map<String, Object> lead,
        Map<String, Map<String, Object>> stepOutputs) {

    public record RunMetadata(long runId, String workflowKey, long workflowVersion) {}
}
