package com.fuba.automation_engine.replay;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * Data shape for a replay fixture. One JSON file per scenario describes a sequence of
 * webhook events with relative timing, the FUB person snapshots the mock client should
 * return, and the expected outcomes asserted by {@link ReplayHarnessTest}.
 *
 * <p>Fixtures live under {@code src/test/resources/replay-fixtures/}. See the README
 * in that directory for the format specification and conventions.
 */
public record ReplayFixture(
        String name,
        String description,
        Map<String, JsonNode> personSnapshots,
        List<ReplayEvent> events,
        @JsonProperty("drainTimeoutMs") Long drainTimeoutMs,
        Expected expected) {

    public long drainTimeoutMsOrDefault() {
        return drainTimeoutMs != null ? drainTimeoutMs : 10_000L;
    }

    /**
     * One webhook event to replay. {@code deltaMs} is the offset from t=0 of the
     * scenario; the harness sleeps between events to honour the relative timing.
     */
    public record ReplayEvent(
            long deltaMs,
            String eventId,
            String event,
            List<Long> resourceIds) {
    }

    /**
     * Assertion expectations for a fixture. All fields are optional; only set the ones
     * relevant to the scenario being validated.
     */
    public record Expected(
            Map<String, Integer> minWorkflowRunsForLead,
            Integer minWebhookEvents,
            Integer minReassignCalls,
            Integer minCreateNoteCalls,
            String notes) {
    }
}
