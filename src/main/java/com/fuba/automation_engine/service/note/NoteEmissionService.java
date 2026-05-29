package com.fuba.automation_engine.service.note;

import com.fuba.automation_engine.service.event.DomainEventEmitter;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.parse.WebhookPayloadExtractors;
import java.util.List;
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

    private final DomainEventEmitter domainEventEmitter;

    public NoteEmissionService(DomainEventEmitter domainEventEmitter) {
        this.domainEventEmitter = domainEventEmitter;
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

        for (Long noteId : noteIds) {
            domainEventEmitter.emit(
                    eventKind,
                    "FUB",
                    event.webhookEventId(),
                    "note",
                    String.valueOf(noteId),
                    event.payload());
        }
        log.info("Note domain event(s) emitted eventId={} eventKind={} noteIdCount={}",
                event.eventId(), eventKind, noteIds.size());
    }
}
