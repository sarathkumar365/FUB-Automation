package com.flux.race.scenarios;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flux.persistence.entity.EventEntity;
import com.flux.race.EngineWriteRaceHarness;
import com.flux.race.FakeFollowUpBossClient;
import com.flux.service.note.NoteEmissionService;
import com.flux.service.webhook.model.NormalizedAction;
import com.flux.service.webhook.model.NormalizedDomain;
import com.flux.service.webhook.model.NormalizedWebhookEvent;
import com.flux.service.webhook.model.WebhookEventStatus;
import com.flux.service.webhook.model.WebhookSource;
import com.flux.service.workflow.StepExecutionContext;
import com.flux.service.workflow.StepExecutionResult;
import com.flux.service.workflow.steps.FubCreateNoteWorkflowStep;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D1/D3/D4 per phase-3-race-matrix.md §D (fub_create_note — entity creation).
 *
 * <p>3e uses the coordinator's tracker-only <b>entity-create</b> mode, recording
 * a <b>single channel</b> keyed on the returned FUB note id. There is no
 * person-side channel: creating a note does not trigger a {@code peopleUpdated}
 * webhook (FUB docs + empirical 2026-06-01), so no person event is ever produced
 * to annotate. The original two-channel plan's D2 is therefore dropped — see the
 * 2026-06-01 changelog in phase-3-plan.md.
 *
 * <p>Like the tag path (3d), this is tracker-only: the engine create itself
 * emits nothing; the {@code notesCreated} echo emits the {@code note.created}
 * event, which the tracker then annotates {@code source=ENGINE}.
 */
class CreateNoteScenariosTest extends EngineWriteRaceHarness {

    private static final String PERSON = "20235";

    @Autowired private FubCreateNoteWorkflowStep createNoteStep;
    @Autowired private NoteEmissionService noteEmissionService;

    // ─── D1: happy path ────────────────────────────────────────────────────
    @Test
    void d1_engineCreatesNote_echoEmitsNoteCreatedAnnotatedEngine() {
        StepExecutionResult result = runCreateNote("Engine note");
        assertTrue(result.success(), "engine note creation succeeds");
        long noteId = noteIdOf(result);
        assertEquals(0, eventRepository.count(),
                "entity-create mode emits nothing on the engine write; echo hasn't arrived yet");

        fireNoteEcho(noteId); // FUB confirms the note via notesCreated webhook

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(1, events.size(), "D1: notesCreated echo → exactly one note.created event");
        assertEquals("note.created", events.get(0).getEventKind());
        assertEquals("ENGINE", events.get(0).getPayload().get("source").asText(),
                "D1: tracker recorded (note, " + noteId + ", {created}); echo annotated source=ENGINE");
        assertEquals(1, fakeFub.callsOf(FakeFollowUpBossClient.Method.CREATE_NOTE).size(),
                "exactly one FUB createNote call");
    }

    // ─── D3: early-echo race (documented, not fixed) ───────────────────────
    // If FUB's notesCreated webhook arrives BEFORE our createNote POST returns,
    // the tracker record doesn't exist yet (entity-create records AFTER the FUB
    // call returns) → the echo is processed unannotated. Empirically rare; a
    // content-hash key would close it but is deferred (phase-3-plan §3e).
    @Test
    void d3_earlyEcho_beforeCreateNoteReturns_notAnnotated() throws Exception {
        fakeFub.setBehavior(FakeFollowUpBossClient.Method.CREATE_NOTE,
                new FakeFollowUpBossClient.Behavior.Ok(300));
        // reset() set the note-id sequence so this test's first note is 1001.
        long expectedNoteId = 1001L;

        raceExecutor.submit(() -> runCreateNote("Slow note"));
        Thread.sleep(50); // createNote still in flight; tracker not yet recorded

        fireNoteEcho(expectedNoteId); // echo beats the POST response

        drain(5); // engine completes → records tracker (too late for the echo above)

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(1, events.size(), "D3: the early echo still emits one note.created");
        assertFalse(events.get(0).getPayload().has("source"),
                "D3 early-echo race: tracker record didn't exist when the echo was processed → NOT annotated. "
                        + "If this ever fails (annotation present), the race-window assumption changed.");
    }

    // ─── D4: multiple engine notes ─────────────────────────────────────────
    @Test
    void d4_threeEngineNotes_allEchoesAnnotated() {
        long n1 = noteIdOf(runCreateNote("note 1"));
        long n2 = noteIdOf(runCreateNote("note 2"));
        long n3 = noteIdOf(runCreateNote("note 3"));

        fireNoteEcho(n1);
        fireNoteEcho(n2);
        fireNoteEcho(n3);

        List<EventEntity> events = eventRepository.findAll();
        assertEquals(3, events.size(), "D4: three notesCreated echoes → three note.created events");
        for (EventEntity ev : events) {
            assertEquals("ENGINE", ev.getPayload().get("source").asText(),
                    "D4: tracker has a record per engine note → all three echoes annotated");
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private StepExecutionResult runCreateNote(String message) {
        StepExecutionContext ctx = new StepExecutionContext(
                /* runId */ 42L,
                /* stepId */ 7L,
                /* nodeId */ "create-note-node",
                /* sourcePersonId */ PERSON,
                /* rawConfig */ Map.of(),
                /* resolvedConfig */ Map.of("message", message),
                /* runContext */ null);
        return createNoteStep.execute(ctx);
    }

    private long noteIdOf(StepExecutionResult result) {
        return ((Number) result.outputs().get("noteId")).longValue();
    }

    // Fire a notesCreated echo straight through NoteEmissionService (the seam
    // WebhookEventProcessorService would call) — HTTP is irrelevant to the race.
    private void fireNoteEcho(long noteId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", "notesCreated");
        payload.putArray("resourceIds").add(noteId);
        NormalizedWebhookEvent event = new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-note-" + noteId,
                "notesCreated",
                null,
                null,
                NormalizedDomain.NOTE,
                NormalizedAction.CREATED,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(),
                "hash-" + noteId,
                null);
        noteEmissionService.emit(event);
    }
}
