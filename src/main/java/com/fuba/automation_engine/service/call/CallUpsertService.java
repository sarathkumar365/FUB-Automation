package com.fuba.automation_engine.service.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.event.DomainEventEmitter;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.person.PersonUpsertService;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence for inbound call webhooks. Extracted from
 * {@code WebhookEventProcessorService} so the save runs inside a real
 * transaction — sub-phase 2d injects {@code DomainEventEmitter} here and
 * emits {@code call.created} after the save, atomically.
 */
@Service
public class CallUpsertService {

    public static final String EVENT_KIND_CALL_CREATED = "call.created";
    public static final String ENTITY_TYPE_CALL = "call";
    public static final String SOURCE_SYSTEM_FUB = "FUB";

    private static final Logger log = LoggerFactory.getLogger(CallUpsertService.class);

    private final ProcessedCallRepository processedCallRepository;
    private final PersonRepository personRepository;
    private final DomainEventEmitter domainEventEmitter;
    private final ObjectMapper objectMapper;

    public CallUpsertService(
            ProcessedCallRepository processedCallRepository,
            PersonRepository personRepository,
            DomainEventEmitter domainEventEmitter,
            ObjectMapper objectMapper) {
        this.processedCallRepository = processedCallRepository;
        this.personRepository = personRepository;
        this.domainEventEmitter = domainEventEmitter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void persistCallFacts(NormalizedWebhookEvent event, ProcessedCallEntity entity, CallDetails callDetails) {
        String sourcePersonId = callDetails.personId() == null ? null : String.valueOf(callDetails.personId());
        entity.setSourcePersonId(sourcePersonId);
        entity.setSourceUserId(callDetails.userId());
        entity.setIsIncoming(callDetails.isIncoming());
        entity.setDurationSeconds(callDetails.duration());
        entity.setOutcome(callDetails.outcome());
        entity.setCallStartedAt(callDetails.createdAt());
        entity.setUpdatedAt(OffsetDateTime.now());
        processedCallRepository.save(entity);

        // source_event_id is the webhook id (FK to webhook_events), NOT the FUB
        // call id — that's the entity_id.
        JsonNode payload = buildCallCreatedPayload(callDetails);
        domainEventEmitter.emit(
                EVENT_KIND_CALL_CREATED,
                SOURCE_SYSTEM_FUB,
                event.webhookEventId(),
                ENTITY_TYPE_CALL,
                String.valueOf(entity.getCallId()),
                payload);

        if (sourcePersonId == null || sourcePersonId.isBlank()) {
            return;
        }
        if (personRepository.findBySourceSystemAndSourcePersonId(PersonUpsertService.SOURCE_SYSTEM_FUB, sourcePersonId).isEmpty()) {
            log.warn(
                    "person-missing-on-call eventId={} callId={} sourcePersonId={} sourceEventType={}",
                    event.eventId(),
                    entity.getCallId(),
                    sourcePersonId,
                    event.sourceEventType());
        }
    }

    /**
     * Build payload manually so emission doesn't depend on jackson-datatype-jsr310
     * being on the classpath. createdAt is rendered as an ISO-8601 string.
     */
    private JsonNode buildCallCreatedPayload(CallDetails callDetails) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (callDetails.id() != null) payload.put("id", callDetails.id());
        if (callDetails.personId() != null) payload.put("personId", callDetails.personId());
        if (callDetails.userId() != null) payload.put("userId", callDetails.userId());
        if (callDetails.duration() != null) payload.put("duration", callDetails.duration());
        if (callDetails.outcome() != null) payload.put("outcome", callDetails.outcome());
        if (callDetails.isIncoming() != null) payload.put("isIncoming", callDetails.isIncoming());
        if (callDetails.createdAt() != null) payload.put("createdAt", callDetails.createdAt().toString());
        return payload;
    }
}
