package com.fuba.automation_engine.race.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.persistence.entity.EventEntity;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.race.EngineWriteRaceHarness;
import com.fuba.automation_engine.race.FakeFollowUpBossClient;
import com.fuba.automation_engine.service.person.PersonUpsertService;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.steps.FubMoveToPondWorkflowStep;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pond-field mirror of {@link ReassignScenariosTest}. 3c routes
 * {@code fub_move_to_pond} through the same scalar-mode coordinator path that
 * 3b proved for {@code fub_reassign}.
 *
 * <p>Only A1, A3, A4, A5 are mirrored here. A2 (lock-not-held smoking gun),
 * A6 (transient+concurrent), A7 (two writes back-to-back) are coordinator-level
 * properties already proven in {@link ReassignScenariosTest} and are not
 * field-specific — re-testing them per step adds runtime without coverage.
 *
 * <p>Conventions: PERSON 20235 seeded with assignedPondId=10 (POND_A).
 * POND_B=20, POND_C=30 are target ponds.
 */
class MoveToPondScenariosTest extends EngineWriteRaceHarness {

    private static final String PERSON = "20235";
    private static final long POND_A = 10L;
    private static final long POND_B = 20L;
    private static final long POND_C = 30L;

    @Autowired private FubMoveToPondWorkflowStep moveToPondStep;
    @Autowired private PersonUpsertService personUpsertService;

    // ─── A1: happy path ────────────────────────────────────────────────────
    @Test
    void a1_singleEngineMove_echoArrivesLater_zeroEvents() {
        seedPerson(PERSON, "{\"assignedPondId\": " + POND_A + ", \"stage\": \"Lead\"}");

        runMove(POND_B);
        fireEcho(PERSON, POND_B); // FUB tells us assignedPondId is now POND_B

        assertEquals(0, eventRepository.count(),
                "A1: engine writes local, echo's diff is empty, zero events emitted");
        assertEquals(POND_B, currentAssignedPondId(PERSON),
                "local must reflect the engine write");
        assertEquals(1, fakeFub.callsOf(FakeFollowUpBossClient.Method.MOVE_TO_POND).size(),
                "exactly one FUB call");
    }

    // ─── A3: FUB-burst echo (N webhooks for one engine write) ──────────────
    @Test
    void a3_fubBurstEcho_threeIdenticalWebhooks_zeroEvents() {
        seedPerson(PERSON, "{\"assignedPondId\": " + POND_A + ", \"stage\": \"Lead\"}");

        runMove(POND_B);
        fireEcho(PERSON, POND_B);
        fireEcho(PERSON, POND_B);
        fireEcho(PERSON, POND_B);

        assertEquals(0, eventRepository.count(),
                "A3: 3 burst echoes all see local == payload, zero events");
        assertTrue(tracker.findMatching(
                "person", PERSON, java.util.Set.of("assignedPondId"),
                java.time.OffsetDateTime.now()).isPresent(),
                "tracker still has the engine record");
    }

    // ─── A4: real concurrent external change ───────────────────────────────
    // Engine moves to POND_B; before engine's FUB PUT lands, an external user
    // moves to POND_C via FUB UI. Both events are annotated source=ENGINE under
    // the subset-match rule (engine.changedFields ⊆ diffFields). This is the
    // documented annotation-over-detection bias.
    @Test
    void a4_concurrentExternalChange_bothEventsAnnotatedEngine_subsetBias() throws Exception {
        seedPerson(PERSON, "{\"assignedPondId\": " + POND_A + ", \"stage\": \"Lead\"}");
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.MOVE_TO_POND,
                new FakeFollowUpBossClient.Behavior.Ok(300));

        raceExecutor.submit(() -> runMove(POND_B));
        Thread.sleep(50); // let engine's inner-tx commit, then mid-flight FUB

        // External-W: someone in FUB UI sets POND_C while our FUB PUT is in flight.
        fireEcho(PERSON, POND_C);

        drain(5);

        // Engine's PUT eventually lands at FUB; FUB sends echo with POND_B.
        fireEcho(PERSON, POND_B);

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(2, events.size(),
                "A4: external change emits 1; engine's late echo emits 1 phantom; total 2");
        for (EventEntity ev : events) {
            assertEquals("ENGINE", ev.getPayload().get("source").asText(),
                    "A4 annotation-over-detection bias: both events annotated source=ENGINE "
                            + "because engine.changedFields={assignedPondId} ⊆ diffFields={assignedPondId}.");
        }
    }

    // ─── A5: permanent FUB failure ─────────────────────────────────────────
    // No revert: local stays at POND_B even though FUB rejected. Next webhook
    // with FUB ground-truth (still POND_A) emits a misleading echo annotated
    // source=ENGINE. Known-issue #26.
    @Test
    void a5_permanentFubFailure_noRevert_misleadingEchoAnnotatedEngine() {
        seedPerson(PERSON, "{\"assignedPondId\": " + POND_A + ", \"stage\": \"Lead\"}");
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.MOVE_TO_POND,
                new FakeFollowUpBossClient.Behavior.Permanent(0, 400));

        StepExecutionResult result = runMove(POND_B);

        assertEquals(FubMoveToPondWorkflowStep.FUB_MOVE_PERMANENT, result.resultCode(),
                "permanent FUB failure propagates as FUB_MOVE_PERMANENT");
        assertEquals(POND_B, currentAssignedPondId(PERSON),
                "A5 no-revert: local stays at engine's intended value despite FUB rejection");
        assertEquals(0, eventRepository.count(),
                "engine writes don't emit (emit-events=false); zero events so far");

        // Next webhook arrives with FUB's actual state (still POND_A).
        fireEcho(PERSON, POND_A);

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(1, events.size(),
                "A5: misleading echo emits 1 event looking like POND_B→POND_A transition");
        assertEquals("ENGINE", events.get(0).getPayload().get("source").asText(),
                "the misleading echo IS annotated source=ENGINE (subset match on assignedPondId). "
                        + "Known-issue #26.");
        assertEquals(POND_A, currentAssignedPondId(PERSON),
                "local re-syncs to FUB ground truth after the echo");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private StepExecutionResult runMove(long targetPondId) {
        StepExecutionContext ctx = new StepExecutionContext(
                /* runId */ 42L,
                /* stepId */ 7L,
                /* nodeId */ "move-to-pond-node",
                /* sourcePersonId */ PERSON,
                /* rawConfig */ Map.of(),
                /* resolvedConfig */ Map.of("targetPondId", targetPondId),
                /* runContext */ null);
        return moveToPondStep.execute(ctx);
    }

    // Build via JSON parsing so numeric node types match what real FUB webhooks
    // deliver — Jackson picks IntNode/LongNode based on value range, and
    // PersonDiffComputer's JsonNode.equals is type-strict.
    private void fireEcho(String personId, long assignedPondId) {
        try {
            JsonNode payload = objectMapper.readTree(
                    "{\"id\":" + personId
                            + ",\"assignedPondId\":" + assignedPondId
                            + ",\"stage\":\"Lead\"}");
            personUpsertService.upsertFubPerson(personId, payload, /* webhookEventId */ null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long currentAssignedPondId(String personId) {
        PersonEntity p = personRepository
                .findBySourceSystemAndSourcePersonId(PersonUpsertService.SOURCE_SYSTEM_FUB, personId)
                .orElseThrow();
        JsonNode v = p.getPersonDetails().get("assignedPondId");
        assertNotNull(v, "assignedPondId must be present in personDetails");
        return v.asLong();
    }
}
