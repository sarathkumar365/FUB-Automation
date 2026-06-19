package com.flux.service.workflow.spi;

import java.time.Instant;

/**
 * Business-free inputs handed to every {@link RunContextContributor}, once per
 * step build. Carries only loose scalars so the contributor contract stays
 * domain-agnostic.
 */
public record RunContextRequest(
        long runId,
        String workflowKey,
        String subjectId,
        String triggerEventId,
        Instant now) {
}
