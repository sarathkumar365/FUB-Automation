package com.fuba.automation_engine.race.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.persistence.entity.EventEntity;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.race.EngineWriteRaceHarness;
import com.fuba.automation_engine.race.FakeFollowUpBossClient;
import com.fuba.automation_engine.service.person.PersonUpsertService;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.steps.FubAddTagWorkflowStep;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1–C3 per phase-3-race-matrix.md §C (fub_add_tag — accumulating field).
 *
 * <p>3d uses the coordinator's <b>tracker-only append</b> mode — the opposite
 * ordering from scalar mode (3b/3c). FUB is called first; the tag-add is
 * recorded on the tracker only on FUB success; local state is <b>never</b>
 * written optimistically. The whole point: an optimistic local {@code [A,B,NEW]}
 * before FUB confirms would make a concurrent external webhook (carrying FUB's
 * truth {@code [A,B,X]}, no NEW yet) diff to a fabricated "NEW removed" event.
 * Tracker-only eliminates that phantom-removal class structurally.
 *
 * <p>Key contrast with the scalar A-cells: scalar mode suppresses the echo to
 * <b>zero</b> events; tracker-only mode emits <b>one annotated</b> event per
 * engine tag-add (local started behind FUB, so the echo legitimately diffs).
 *
 * <p>Conventions: PERSON 20235 seeded with tags {@code [A,B]}.
 */
class AddTagScenariosTest extends EngineWriteRaceHarness {

    private static final String PERSON = "20235";

    @Autowired private FubAddTagWorkflowStep addTagStep;
    @Autowired private PersonUpsertService personUpsertService;

    // ─── C1: happy path ────────────────────────────────────────────────────
    @Test
    void c1_engineTagAdd_echoEmitsOneEventAnnotatedEngine() {
        seedPerson(PERSON, "{\"tags\":[\"A\",\"B\"],\"stage\":\"Lead\"}");

        StepExecutionResult result = runAddTag("NEW");
        assertTrue(result.success(), "engine tag-add succeeds");
        assertEquals(0, eventRepository.count(),
                "tracker-only: no local write and append mode doesn't emit; echo hasn't arrived yet");

        fireTagsEcho(PERSON, "A", "B", "NEW"); // FUB confirms the tag landed

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(1, events.size(),
                "C1: tracker-only means the echo DOES diff (local was never optimistically written) → 1 event. "
                        + "Contrast with scalar A1 which suppresses to 0.");
        assertEquals("ENGINE", events.get(0).getPayload().get("source").asText(),
                "C1: tracker recorded changedFields={tags}; echo diff {tags} ⊆ {tags} → annotated ENGINE");
        assertEquals(1, fakeFub.callsOf(FakeFollowUpBossClient.Method.ADD_TAG).size(),
                "exactly one FUB call");
    }

    // ─── C2: real concurrent external add ──────────────────────────────────
    // Engine adds NEW (FUB in flight); before engine's FUB returns (so before
    // the tracker record exists), an external user adds X and that webhook
    // arrives. Then engine's tag lands and its echo carries both. Expect: the
    // external add is a real UNannotated event (no tracker record yet), the
    // engine echo is annotated ENGINE, and crucially NO fabricated "removed"
    // event — local was never optimistically written.
    @Test
    void c2_externalConcurrentAdd_noPhantomRemoval_externalUnannotated_engineEchoAnnotated() throws Exception {
        seedPerson(PERSON, "{\"tags\":[\"A\",\"B\"],\"stage\":\"Lead\"}");
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.ADD_TAG,
                new FakeFollowUpBossClient.Behavior.Ok(300));

        raceExecutor.submit(() -> runAddTag("NEW"));
        Thread.sleep(50); // engine's FUB call in flight; tracker NOT yet recorded (append records AFTER FUB returns)

        // External-W: someone adds X in FUB UI; webhook arrives before our FUB PUT completes.
        fireTagsEcho(PERSON, "A", "B", "X");

        drain(5); // engine FUB completes → tracker records {tags}

        // Engine's tag eventually lands at FUB; echo carries both X and NEW.
        fireTagsEcho(PERSON, "A", "B", "X", "NEW");

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(2, events.size(),
                "C2: external add emits 1 (unannotated — no tracker record existed yet); "
                        + "engine echo emits 1 (annotated). NO phantom 'NEW removed' event.");

        long annotated = events.stream()
                .filter(e -> e.getPayload().has("source"))
                .filter(e -> "ENGINE".equals(e.getPayload().get("source").asText()))
                .count();
        assertEquals(1, annotated,
                "C2: exactly one event annotated ENGINE (the engine echo); the external add is a real event. "
                        + "Tracker-only ordering (FUB→record) is what keeps the external add unannotated.");
    }

    // ─── C3: FUB failure ───────────────────────────────────────────────────
    // FUB rejects the tag-add. Coordinator never records (append records only on
    // FUB success); local was never touched. No phantom event is even possible.
    @Test
    void c3_fubFailure_noTrackerRecord_noLocalChange_noPhantomPossible() {
        seedPerson(PERSON, "{\"tags\":[\"A\",\"B\"],\"stage\":\"Lead\"}");
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.ADD_TAG,
                new FakeFollowUpBossClient.Behavior.Permanent(0, 400));

        StepExecutionResult result = runAddTag("NEW");

        assertEquals(FubAddTagWorkflowStep.FAILED, result.resultCode(),
                "permanent FUB failure propagates as FAILED");
        assertEquals(0, eventRepository.count(),
                "no echo and append mode doesn't emit → 0 events");
        assertTrue(tracker.findMatching("person", PERSON, Set.of("tags"), OffsetDateTime.now()).isEmpty(),
                "C3: FUB failed before the record step → tracker has no entry for this person");
        assertEquals(List.of("A", "B"), currentTags(PERSON),
                "C3: no local change — tracker-only never writes local optimistically");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private StepExecutionResult runAddTag(String tagName) {
        StepExecutionContext ctx = new StepExecutionContext(
                /* runId */ 42L,
                /* stepId */ 7L,
                /* nodeId */ "add-tag-node",
                /* sourcePersonId */ PERSON,
                /* rawConfig */ Map.of(),
                /* resolvedConfig */ Map.of("tagName", tagName),
                /* runContext */ null);
        return addTagStep.execute(ctx);
    }

    // Build via JSON parsing so the payload matches what a real FUB webhook
    // delivers; PersonDiffComputer sorts string arrays before comparing.
    private void fireTagsEcho(String personId, String... tags) {
        try {
            String arr = java.util.Arrays.stream(tags)
                    .map(t -> "\"" + t + "\"")
                    .collect(Collectors.joining(","));
            JsonNode payload = objectMapper.readTree(
                    "{\"id\":" + personId + ",\"tags\":[" + arr + "],\"stage\":\"Lead\"}");
            personUpsertService.upsertFubPerson(personId, payload, /* webhookEventId */ null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> currentTags(String personId) {
        PersonEntity p = personRepository
                .findBySourceSystemAndSourcePersonId(PersonUpsertService.SOURCE_SYSTEM_FUB, personId)
                .orElseThrow();
        JsonNode tags = p.getPersonDetails().get("tags");
        List<String> out = new ArrayList<>();
        if (tags != null && tags.isArray()) {
            tags.forEach(t -> out.add(t.asText()));
        }
        return out;
    }
}
