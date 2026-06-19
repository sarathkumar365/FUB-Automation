package com.flux.replay;

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
        List<WorkflowSpec> workflows,
        List<ReplayEvent> events,
        @JsonProperty("drainTimeoutMs") Long drainTimeoutMs,
        Expected expected) {

    public long drainTimeoutMsOrDefault() {
        return drainTimeoutMs != null ? drainTimeoutMs : 10_000L;
    }

    /**
     * One webhook event to replay. {@code deltaMs} is the offset from t=0 of the
     * scenario; the harness sleeps between events to honour the relative timing.
     *
     * <p>{@code personSnapshots} (optional) overrides what the mock FUB client returns
     * for {@code getPersonRawById} from this event onward — modelling FUB's ID-only
     * webhooks where the follow-up GET returns state-at-read-time. This is what lets a
     * fixture express state *changing* between events (reassignment, stage change,
     * contacted flip), keyed by person id (string).
     */
    public record ReplayEvent(
            long deltaMs,
            String eventId,
            String event,
            List<Long> resourceIds,
            Map<String, JsonNode> personSnapshots) {
    }

    /**
     * A workflow to seed for the scenario. When a fixture omits {@code workflows}, the
     * harness seeds its default {@code person.created} workflow (back-compat). {@code graph}
     * is optional — omit it for the harness's default trivial graph.
     */
    public record WorkflowSpec(
            String key,
            Map<String, Object> trigger,
            Map<String, Object> graph) {
    }

    /**
     * Assertion expectations for a fixture. All fields are optional; only set the ones
     * relevant to the scenario being validated.
     */
    public record Expected(
            Map<String, Integer> minWorkflowRunsForPerson,
            Integer minWebhookEvents,
            Integer minReassignCalls,
            Integer minCreateNoteCalls,
            // Domain-event assertions (Phase 2). Exact-count for state-change
            // events catches both undershoot (collapse missed) AND overshoot
            // (collapse broken silently). min for append events since no
            // uniqueness claim applies. Per-person counts so multi-person
            // fixtures can be unambiguous.
            Map<String, Integer> expectedCreatedEventsForPerson,
            Map<String, Integer> expectedStateChangeEventsForPerson,
            Map<String, Integer> minAppendEvents,
            // Phase 5 run-collision assertions. expectedTotalRunsForPerson is exact
            // across all statuses; expectedSupersededRunsForPerson is exact for runs
            // ending CANCELED with reason_code SUPERSEDED_BY_NEWER_EVENT. Together they
            // pin down "N runs created, M superseded, (N-M) survive".
            Map<String, Integer> expectedTotalRunsForPerson,
            Map<String, Integer> expectedSupersededRunsForPerson,
            String notes) {
    }
}
