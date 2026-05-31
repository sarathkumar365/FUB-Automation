package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.EventEntity;
import com.fuba.automation_engine.persistence.repository.EventRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Central emission point for domain events. Every {@code person.*},
 * {@code call.*}, {@code note.*} (and future event_kind) row written to the
 * {@code events} table flows through {@link #emit}.
 *
 * <p><b>Semantics — load-bearing, do not relax without re-reading the plan:</b>
 * <ul>
 *   <li><b>{@link Propagation#MANDATORY MANDATORY} propagation.</b> The caller
 *       MUST already be inside a {@code @Transactional} method. The INSERT
 *       happens inside the caller's transaction so the event row is atomic
 *       with the state change that produced it. A caller that forgets the
 *       annotation gets a loud {@code IllegalTransactionStateException}
 *       instead of silently producing un-dispatched / lost events. Phase 2's
 *       known emission sites are all {@code @Transactional} by construction:
 *       {@code PersonUpsertService.upsertFubPerson},
 *       {@code CallUpsertService.persistCallFacts},
 *       {@code WebhookEventProcessorService.processNoteDomainEvent}.</li>
 *   <li><b>After-commit dispatch.</b> The dispatcher is invoked from a
 *       {@link TransactionSynchronization#afterCommit} hook, not inline. Two
 *       reasons: (a) inline dispatch would extend lock-hold on the
 *       state-changed row across listener work, and once Phase 4 wires the
 *       trigger router as a listener that drags workflow planning into the
 *       upsert transaction; (b) the event row is already durable by the time
 *       any listener runs, so a listener failure cannot lose the event.</li>
 *   <li><b>Rollback semantics.</b> If the caller's transaction rolls back,
 *       Spring discards the registered synchronization automatically — no
 *       dispatch fires. Tested by {@code DomainEventEmitterTest} case (b).</li>
 * </ul>
 *
 * <p>This is NOT the entry point for external events (raw webhooks). That
 * boundary lives in the webhook layer ({@code WebhookIngressService} →
 * {@code WebhookEventProcessorService}). The emitter sits downstream of the
 * upsert services — by the time it's called, the engine has already read the
 * new state and decided something actually changed (state-change events) or
 * something happened that's worth recording (append events). See
 * {@code Docs/features/domain-events/plan.md} §"Conceptual framing".
 */
@Service
public class DomainEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(DomainEventEmitter.class);

    static final String ANNOTATION_SOURCE_KEY = "source";
    static final String ANNOTATION_SOURCE_ENGINE = "ENGINE";

    private final EventRepository eventRepository;
    private final DomainEventDispatcher dispatcher;
    private final EngineWriteTracker engineWriteTracker;
    private final ObjectMapper objectMapper;

    public DomainEventEmitter(
            EventRepository eventRepository,
            DomainEventDispatcher dispatcher,
            EngineWriteTracker engineWriteTracker,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.dispatcher = dispatcher;
        this.engineWriteTracker = engineWriteTracker;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emit(
            String eventKind,
            String sourceSystem,
            Long sourceEventId,
            String entityType,
            String entityId,
            JsonNode payload) {

        // Tracker annotation — consult before persist so the annotation lands
        // in both the DB row and the dispatched record. Mutates the payload
        // in place if it's an ObjectNode; otherwise wraps in one.
        JsonNode annotatedPayload = maybeAnnotateEngineSource(entityType, entityId, payload);

        EventEntity entity = new EventEntity();
        entity.setEventKind(eventKind);
        entity.setSourceSystem(sourceSystem);
        entity.setSourceEventId(sourceEventId);
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setPayload(annotatedPayload);
        entity.setCreatedAt(OffsetDateTime.now());

        EventEntity saved = eventRepository.save(entity);
        log.info(
                "Domain event persisted eventKind={} sourceSystem={} entityType={} entityId={} eventId={}",
                eventKind,
                sourceSystem,
                entityType,
                entityId,
                saved.getId());

        DomainEvent dispatchEvent = new DomainEvent(
                eventKind,
                sourceSystem,
                sourceEventId,
                entityType,
                entityId,
                annotatedPayload);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.dispatch(dispatchEvent);
            }
        });
    }

    // Caller-set source wins; append/create (no changed_fields) goes unannotated until 3d/3e.
    private JsonNode maybeAnnotateEngineSource(String entityType, String entityId, JsonNode payload) {
        if (entityType == null || entityId == null || payload == null) {
            return payload;
        }
        if (payload.has(ANNOTATION_SOURCE_KEY) && !payload.get(ANNOTATION_SOURCE_KEY).isNull()) {
            return payload;
        }
        Set<String> diffFields = extractChangedFields(payload);
        if (diffFields.isEmpty()) {
            return payload;
        }

        Optional<EngineWriteRecord> hit = engineWriteTracker.findMatching(
                entityType, entityId, diffFields, OffsetDateTime.now());
        if (hit.isEmpty()) {
            return payload;
        }

        ObjectNode annotated = (payload instanceof ObjectNode obj)
                ? obj
                : objectMapper.createObjectNode().setAll((ObjectNode) objectMapper.valueToTree(payload));
        annotated.put(ANNOTATION_SOURCE_KEY, ANNOTATION_SOURCE_ENGINE);
        log.info("Event annotated source=ENGINE entityType={} entityId={} matchedRecordId={}",
                entityType, entityId, hit.get().id());
        return annotated;
    }

    private Set<String> extractChangedFields(JsonNode payload) {
        JsonNode node = payload.get("changed_fields");
        if (node == null || !node.isArray() || node.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>(node.size());
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            JsonNode field = it.next();
            if (field.isTextual()) {
                out.add(field.asText());
            }
        }
        return out;
    }
}
