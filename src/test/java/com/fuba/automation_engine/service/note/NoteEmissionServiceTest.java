package com.fuba.automation_engine.service.note;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.service.event.DomainEventEmitter;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NoteEmissionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DomainEventEmitter emitter;
    private NoteEmissionService service;

    @BeforeEach
    void setUp() {
        emitter = mock(DomainEventEmitter.class);
        service = new NoteEmissionService(emitter);
    }

    @Test
    void notesCreatedEmitsExactlyOneNoteCreatedEventPerResourceId() {
        NormalizedWebhookEvent event = noteEvent(NormalizedAction.CREATED, "notesCreated", 9001L);

        service.emit(event);

        verify(emitter, times(1)).emit(
                eq("note.created"), eq("FUB"), eq(7L), eq("note"), eq("9001"),
                any(JsonNode.class));
    }

    @Test
    void notesUpdatedMapsToNoteUpdatedEventKind() {
        NormalizedWebhookEvent event = noteEvent(NormalizedAction.UPDATED, "notesUpdated", 9002L);
        service.emit(event);
        verify(emitter).emit(eq("note.updated"), anyString(), any(), eq("note"), eq("9002"), any(JsonNode.class));
    }

    @Test
    void notesDeletedMapsToNoteDeletedEventKind() {
        NormalizedWebhookEvent event = noteEvent(NormalizedAction.DELETED, "notesDeleted", 9003L);
        service.emit(event);
        verify(emitter).emit(eq("note.deleted"), anyString(), any(), eq("note"), eq("9003"), any(JsonNode.class));
    }

    @Test
    void notesWithoutResourceIdsEmitsNothing() {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", "notesCreated");
        NormalizedWebhookEvent event = eventOf(NormalizedAction.CREATED, payload);

        service.emit(event);

        verifyNoInteractions(emitter);
    }

    @Test
    void notesWithMultipleResourceIdsEmitsOneEventPerNote() {
        NormalizedWebhookEvent event = noteEvent(NormalizedAction.CREATED, "notesCreated", 9010L, 9011L, 9012L);
        service.emit(event);
        verify(emitter, times(3)).emit(
                eq("note.created"), anyString(), any(), eq("note"), anyString(), any(JsonNode.class));
    }

    @Test
    void notesEventPayloadPassesThroughWebhookPayloadByReference() {
        NormalizedWebhookEvent event = noteEvent(NormalizedAction.CREATED, "notesCreated", 9001L);

        service.emit(event);

        ArgumentCaptor<JsonNode> cap = ArgumentCaptor.forClass(JsonNode.class);
        verify(emitter).emit(anyString(), anyString(), any(), anyString(), anyString(), cap.capture());
        assertSame(event.payload(), cap.getValue(),
                "note payload must be the raw webhook payload — workflows can fetch body content on demand");
    }

    @Test
    void unmappedActionSkipsEmission() {
        NormalizedWebhookEvent event = noteEvent(NormalizedAction.UNKNOWN, "notesWeird", 9999L);
        service.emit(event);
        verifyNoInteractions(emitter);
    }

    // ---------- helpers ----------

    private NormalizedWebhookEvent noteEvent(NormalizedAction action, String type, long... resourceIds) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", type);
        var ids = payload.putArray("resourceIds");
        for (long id : resourceIds) ids.add(id);
        return eventOf(action, payload);
    }

    private NormalizedWebhookEvent eventOf(NormalizedAction action, ObjectNode payload) {
        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-note",
                payload.path("eventType").asText(""),
                null,
                null,
                NormalizedDomain.NOTE,
                action,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(),
                "hash",
                7L);
    }
}
