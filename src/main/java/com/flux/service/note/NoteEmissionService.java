package com.flux.service.note;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flux.service.event.DomainEventEmitter;
import com.flux.service.event.EngineWriteRecord;
import com.flux.service.event.EngineWriteTracker;
import com.flux.service.webhook.model.NormalizedAction;
import com.flux.service.webhook.model.NormalizedWebhookEvent;
import com.flux.service.webhook.parse.WebhookPayloadExtractors;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Note webhook → domain event. Exists as a separate @Component so the call
 * from WebhookEventProcessorService.process() goes through Spring's proxy
 * and @Transactional actually applies — self-invocation would bypass it,
 * tripping DomainEventEmitter's MANDATORY propagation guard.
 *
 * <p>No notes table. Body content is not fetched from /v1/notes/{id}; workflows
 * that need it can add a fetch path when a real consumer arrives.
 */
@Service
public class NoteEmissionService {

    private static final Logger log = LoggerFactory.getLogger(NoteEmissionService.class);

    private static final String ANNOTATION_SOURCE_KEY = "source";
    private static final String ANNOTATION_SOURCE_ENGINE = "ENGINE";

    private final DomainEventEmitter domainEventEmitter;
    private final EngineWriteTracker engineWriteTracker;
    private final ObjectMapper objectMapper;

    public NoteEmissionService(
            DomainEventEmitter domainEventEmitter,
            EngineWriteTracker engineWriteTracker,
            ObjectMapper objectMapper) {
        this.domainEventEmitter = domainEventEmitter;
        this.engineWriteTracker = engineWriteTracker;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void emit(NormalizedWebhookEvent event) {
        NormalizedAction action = event.normalizedAction();
        String eventKind = switch (action == null ? NormalizedAction.UNKNOWN : action) {
            case CREATED -> "note.created";
            case UPDATED -> "note.updated";
            case DELETED -> "note.deleted";
            default -> null;
        };
        if (eventKind == null) {
            log.warn("Skipping NOTE event with unmapped action eventId={} action={} sourceEventType={}",
                    event.eventId(), action, event.sourceEventType());
            return;
        }

        List<Long> noteIds = WebhookPayloadExtractors.extractResourceIdsAsLongs(
                event.payload() == null ? null : event.payload().get("resourceIds"));
        if (noteIds.isEmpty()) {
            log.warn("No note resourceIds present; skipping eventId={} eventKind={}",
                    event.eventId(), eventKind);
            return;
        }

        String changeMarker = switch (action) {
            case CREATED -> "created";
            case UPDATED -> "updated";
            case DELETED -> "deleted";
            default -> null; // unreachable: eventKind null-guard above already returned
        };

        for (Long noteId : noteIds) {
            JsonNode emitPayload = maybeAnnotateEngineSource(
                    String.valueOf(noteId), changeMarker, event.payload());
            domainEventEmitter.emit(
                    eventKind,
                    "FUB",
                    event.webhookEventId(),
                    "note",
                    String.valueOf(noteId),
                    emitPayload);
        }
        log.info("Note domain event(s) emitted eventId={} eventKind={} noteIdCount={}",
                event.eventId(), eventKind, noteIds.size());
    }

    /**
     * Annotate {@code source=ENGINE} when {@code fub_create_note} recorded this
     * note on the tracker (Phase 3e). Hit → deep copy (never mutate the shared
     * payload, reused across resourceIds); miss → original reference unchanged.
     * Done here, not in {@code DomainEventEmitter}'s universal hook, because that
     * hook keys off {@code changed_fields} which note webhooks don't carry.
     */
    private JsonNode maybeAnnotateEngineSource(String noteId, String changeMarker, JsonNode payload) {
        if (changeMarker == null || payload == null) {
            return payload;
        }
        Optional<EngineWriteRecord> hit = engineWriteTracker.findMatching(
                "note", noteId, Set.of(changeMarker), OffsetDateTime.now());
        if (hit.isEmpty()) {
            return payload;
        }
        ObjectNode annotated = (payload instanceof ObjectNode obj)
                ? obj.deepCopy()
                : (ObjectNode) objectMapper.valueToTree(payload);
        annotated.put(ANNOTATION_SOURCE_KEY, ANNOTATION_SOURCE_ENGINE);
        log.info("note.{} annotated source=ENGINE noteId={} matchedRecordId={}",
                changeMarker, noteId, hit.get().id());
        return annotated;
    }
}
