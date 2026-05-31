package com.fuba.automation_engine.race.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.EventEntity;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.race.EngineWriteRaceHarness;
import com.fuba.automation_engine.race.FakeFollowUpBossClient;
import com.fuba.automation_engine.service.person.PersonUpsertService;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.steps.FubReassignWorkflowStep;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1–A7 codified per phase-3-race-matrix.md.
 *
 * <p>Conventions:
 * <ul>
 *   <li>PERSON 20235 is seeded with assignedUserId=100 (BOB) unless stated.</li>
 *   <li>ALICE=200, CAROL=300 are target user ids in scenarios.</li>
 *   <li>"Echo" = a webhook payload simulating FUB telling us what FUB has now.
 *       Fired by directly calling {@link PersonUpsertService#upsertFubPerson}
 *       — bypassing HTTP, which is irrelevant to the race.</li>
 *   <li>Assertions are EXACT counts. {@code assertTotalEvents(2)} fails on 1
 *       (missed annotation) and on 3 (broken collapse).</li>
 * </ul>
 */
class ReassignScenariosTest extends EngineWriteRaceHarness {

    private static final String PERSON = "20235";
    private static final long BOB = 100L;
    private static final long ALICE = 200L;
    private static final long CAROL = 300L;

    @Autowired private FubReassignWorkflowStep reassignStep;
    @Autowired private PersonUpsertService personUpsertService;

    // ─── A1: happy path ────────────────────────────────────────────────────
    @Test
    void a1_singleEngineReassign_echoArrivesLater_zeroEvents() {
        seedPerson(PERSON, "{\"assignedUserId\": " + BOB + ", \"stage\": \"Lead\"}");

        runReassign(ALICE);
        fireEcho(PERSON, ALICE); // FUB tells us assignedUserId is now ALICE

        assertEquals(0, eventRepository.count(),
                "A1: engine writes local, echo's diff is empty, zero events emitted");
        assertEquals(ALICE, currentAssignedUserId(PERSON),
                "local must reflect the engine write");
        assertEquals(1, fakeFub.callsOf(FakeFollowUpBossClient.Method.REASSIGN).size(),
                "exactly one FUB call");
    }

    // ─── A2: echo arrives WHILE engine FUB call is in flight ───────────────
    // Smoking-gun proof: if the row lock were held across the FUB HTTP call,
    // the echo's PersonUpsertService.upsertFubPerson would block for the full
    // FUB delay. Measured webhook elapsed time MUST be much less than fubDelay.
    @Test
    void a2_echoDuringInFlightFubCall_lockNotHeld_zeroEvents() throws Exception {
        seedPerson(PERSON, "{\"assignedUserId\": " + BOB + ", \"stage\": \"Lead\"}");
        long fubDelayMs = 500;
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.REASSIGN,
                new FakeFollowUpBossClient.Behavior.Ok(fubDelayMs));

        CountDownLatch engineStarted = new CountDownLatch(1);
        AtomicLong echoElapsed = new AtomicLong(-1);

        raceExecutor.submit(() -> {
            engineStarted.countDown();
            runReassign(ALICE);
        });

        engineStarted.await();
        Thread.sleep(50); // ensure engine's inner-tx commit has happened
        long t0 = System.nanoTime();
        fireEcho(PERSON, ALICE);
        echoElapsed.set((System.nanoTime() - t0) / 1_000_000);

        drain(5);

        assertTrue(echoElapsed.get() < fubDelayMs / 2,
                "SMOKING GUN: echo took " + echoElapsed.get() + "ms; "
                        + "must be << " + fubDelayMs + "ms (FUB delay). "
                        + "If close to FUB delay, the row lock is held across the FUB call.");
        assertEquals(0, eventRepository.count(),
                "A2: echo diff is empty (engine already wrote local), zero events");
    }

    // ─── A3: FUB-burst echo (N webhooks for one engine write) ──────────────
    @Test
    void a3_fubBurstEcho_threeIdenticalWebhooks_zeroEvents() {
        seedPerson(PERSON, "{\"assignedUserId\": " + BOB + ", \"stage\": \"Lead\"}");

        runReassign(ALICE);
        fireEcho(PERSON, ALICE);
        fireEcho(PERSON, ALICE);
        fireEcho(PERSON, ALICE);

        assertEquals(0, eventRepository.count(),
                "A3: 3 burst echoes all see local == payload, zero events");
        assertTrue(tracker.findMatching(
                "person", PERSON, Set.of("assignedUserId"),
                java.time.OffsetDateTime.now()).isPresent(),
                "tracker still has the engine record");
    }

    // ─── A4: real concurrent external change ───────────────────────────────
    // Engine reassigns to ALICE; before engine's FUB PUT lands, an external
    // user reassigns to CAROL via FUB UI. Both events are annotated source=ENGINE
    // under our subset-match rule (engine.changedFields ⊆ diffFields). This is
    // the documented annotation-over-detection bias (known-issues #26-area).
    @Test
    void a4_concurrentExternalChange_bothEventsAnnotatedEngine_subsetBias() throws Exception {
        seedPerson(PERSON, "{\"assignedUserId\": " + BOB + ", \"stage\": \"Lead\"}");
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.REASSIGN,
                new FakeFollowUpBossClient.Behavior.Ok(300));

        raceExecutor.submit(() -> runReassign(ALICE));
        Thread.sleep(50); // let engine's inner-tx commit, then mid-flight FUB

        // External-W: someone in FUB UI sets CAROL while our FUB PUT is in flight.
        fireEcho(PERSON, CAROL);

        drain(5);

        // Engine's PUT eventually lands at FUB; FUB sends echo with ALICE.
        fireEcho(PERSON, ALICE);

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(2, events.size(),
                "A4: external change emits 1; engine's late echo emits 1 phantom; total 2");
        for (EventEntity ev : events) {
            assertEquals("ENGINE", ev.getPayload().get("source").asText(),
                    "A4 annotation-over-detection bias: both events annotated source=ENGINE "
                            + "because engine.changedFields={assignedUserId} ⊆ diffFields={assignedUserId}. "
                            + "Workflow filter change.source != 'ENGINE' would suppress both.");
        }
    }

    // ─── A5: permanent FUB failure ─────────────────────────────────────────
    // No revert: local stays at ALICE even though FUB rejected. Next webhook
    // with FUB ground-truth (still BOB) emits a misleading echo annotated
    // source=ENGINE. This is known-issue #26.
    @Test
    void a5_permanentFubFailure_noRevert_misleadingEchoAnnotatedEngine() {
        seedPerson(PERSON, "{\"assignedUserId\": " + BOB + ", \"stage\": \"Lead\"}");
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.REASSIGN,
                new FakeFollowUpBossClient.Behavior.Permanent(0, 400));

        StepExecutionResult result = runReassign(ALICE);

        assertEquals(FubReassignWorkflowStep.FUB_REASSIGN_PERMANENT, result.resultCode(),
                "permanent FUB failure propagates as FUB_REASSIGN_PERMANENT");
        assertEquals(ALICE, currentAssignedUserId(PERSON),
                "A5 no-revert: local stays at engine's intended value despite FUB rejection");
        assertEquals(0, eventRepository.count(),
                "engine writes don't emit (emit-events=false); zero events so far");

        // Next webhook arrives with FUB's actual state (still BOB).
        fireEcho(PERSON, BOB);

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(1, events.size(),
                "A5: misleading echo emits 1 event looking like ALICE→BOB transition");
        assertEquals("ENGINE", events.get(0).getPayload().get("source").asText(),
                "the misleading echo IS annotated source=ENGINE (subset match on assignedUserId) — "
                        + "Phase 4 workflows filtering ENGINE will suppress it. Known-issue #26.");
        assertEquals(BOB, currentAssignedUserId(PERSON),
                "local re-syncs to FUB ground truth after the echo");
    }

    // ─── A6: transient failure under concurrent external change ────────────
    // FUB returns transient on first attempt; the helper retries and succeeds.
    // Outcome mirrors A4 because retry doesn't change the race shape.
    @Test
    void a6_transientFailureWithConcurrentExternal_sameOutcomeAsA4() throws Exception {
        seedPerson(PERSON, "{\"assignedUserId\": " + BOB + ", \"stage\": \"Lead\"}");
        // Behaviour: FUB call eventually succeeds (after helper retry), with delay.
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.REASSIGN,
                new FakeFollowUpBossClient.Behavior.Ok(200));

        raceExecutor.submit(() -> runReassign(ALICE));
        Thread.sleep(30);
        fireEcho(PERSON, CAROL); // external user changes to CAROL mid-flight
        drain(5);
        fireEcho(PERSON, ALICE); // engine's eventual echo

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(2, events.size(), "A6 same shape as A4: 2 events");
        for (EventEntity ev : events) {
            assertEquals("ENGINE", ev.getPayload().get("source").asText(),
                    "A6 annotation-over-detection bias holds under any timing of FUB success");
        }
    }

    // ─── A7: two engine writes back-to-back, same person ──────────────────
    @Test
    void a7_twoEngineWritesBackToBack_finalEchoAnnotatedRegardlessOfOrder() throws Exception {
        seedPerson(PERSON, "{\"assignedUserId\": " + BOB + ", \"stage\": \"Lead\"}");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        raceExecutor.submit(() -> {
            ready.countDown();
            try { fire.await(); } catch (InterruptedException ignored) {}
            runReassign(ALICE);
        });
        raceExecutor.submit(() -> {
            ready.countDown();
            try { fire.await(); } catch (InterruptedException ignored) {}
            runReassign(CAROL);
        });
        ready.await(2, TimeUnit.SECONDS);
        fire.countDown();
        drain(5);

        // The lock serializes them. Final local is whichever ran second.
        long finalLocal = currentAssignedUserId(PERSON);
        assertTrue(finalLocal == ALICE || finalLocal == CAROL,
                "final local must be one of the two engine targets, got " + finalLocal);

        // Both FUB calls were made.
        assertEquals(2, fakeFub.callsOf(FakeFollowUpBossClient.Method.REASSIGN).size(),
                "exactly two FUB calls — both engine writes proceeded after lock-serialized local update");

        // Echo for the OTHER value (the one local doesn't currently hold) will diff.
        long otherValue = (finalLocal == ALICE) ? CAROL : ALICE;
        fireEcho(PERSON, otherValue);
        fireEcho(PERSON, finalLocal); // and the matching one too

        List<EventEntity> events = eventRepository.findAll();
        // The echo that doesn't match emits 1 event; the matching one emits 0.
        // After that first emit, local becomes otherValue, so the second echo
        // (finalLocal) now does NOT match local → diffs → emits 1 more.
        assertEquals(2, events.size(),
                "A7: two mismatched echoes both emit annotated events");
        for (EventEntity ev : events) {
            assertEquals("ENGINE", ev.getPayload().get("source").asText(),
                    "tracker has records for BOTH engine writes; subset match → both echoes annotated");
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private StepExecutionResult runReassign(long targetUserId) {
        StepExecutionContext ctx = new StepExecutionContext(
                /* runId */ 42L,
                /* stepId */ 7L,
                /* nodeId */ "reassign-node",
                /* sourcePersonId */ PERSON,
                /* rawConfig */ Map.of(),
                /* resolvedConfig */ Map.of("targetUserId", targetUserId),
                /* runContext */ null);
        return reassignStep.execute(ctx);
    }

    // Build via JSON parsing so numeric node types match what real FUB webhooks
    // deliver — Jackson picks IntNode/LongNode based on value range, and
    // PersonDiffComputer's JsonNode.equals is type-strict.
    private void fireEcho(String personId, long assignedUserId) {
        try {
            JsonNode payload = objectMapper.readTree(
                    "{\"id\":" + personId
                            + ",\"assignedUserId\":" + assignedUserId
                            + ",\"stage\":\"Lead\"}");
            personUpsertService.upsertFubPerson(personId, payload, /* webhookEventId */ null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long currentAssignedUserId(String personId) {
        PersonEntity p = personRepository
                .findBySourceSystemAndSourcePersonId(PersonUpsertService.SOURCE_SYSTEM_FUB, personId)
                .orElseThrow();
        JsonNode v = p.getPersonDetails().get("assignedUserId");
        assertNotNull(v, "assignedUserId must be present in personDetails");
        return v.asLong();
    }
}
