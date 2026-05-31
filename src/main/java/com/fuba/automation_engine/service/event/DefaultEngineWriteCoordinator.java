package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.service.person.PersonUpsertService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// REQUIRES_NEW for inner writes so the row lock is released before the FUB call.
@Service
public class DefaultEngineWriteCoordinator implements EngineWriteCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DefaultEngineWriteCoordinator.class);
    private static final String EVENT_KIND_PERSON_STATE_CHANGED = "person.state_changed";

    private final PersonRepository personRepository;
    private final EngineWriteTracker tracker;
    private final DomainEventEmitter domainEventEmitter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;
    private final boolean emitEvents;

    public DefaultEngineWriteCoordinator(
            PersonRepository personRepository,
            EngineWriteTracker tracker,
            DomainEventEmitter domainEventEmitter,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${engine.write.emit-events:false}") boolean emitEvents) {
        this.personRepository = personRepository;
        this.tracker = tracker;
        this.domainEventEmitter = domainEventEmitter;
        this.objectMapper = objectMapper;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.emitEvents = emitEvents;
        log.info("EngineWriteCoordinator initialized emitEvents={}", emitEvents);
    }

    // a scalar field is a single-value field that gets overwritten on update — the new value fully replaces the old one. 
    // Contrast with accumulating fields which hold multiple values and grow/shrink.
    @Override
    public <T> T applyScalarFieldUpdate(
            String sourcePersonId,
            Map<String, JsonNode> fieldUpdates,
            Long runId,
            Supplier<T> fubCall) {

        validateSourcePersonId(sourcePersonId);
        if (fieldUpdates == null || fieldUpdates.isEmpty()) {
            throw new IllegalArgumentException("fieldUpdates must be non-empty");
        }
        Set<String> changedFields = Set.copyOf(fieldUpdates.keySet());

        requiresNewTx.executeWithoutResult(status -> {
            PersonEntity entity = personRepository
                    .findBySourceSystemAndSourcePersonIdForUpdate(
                            PersonUpsertService.SOURCE_SYSTEM_FUB, sourcePersonId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Engine scalar write targets non-existent person sourcePersonId="
                                    + sourcePersonId));

            JsonNode oldDetails = entity.getPersonDetails();
            ObjectNode mergedDetails = mergeFields(oldDetails, fieldUpdates);
            entity.setPersonDetails(mergedDetails);
            entity.setUpdatedAt(OffsetDateTime.now());
            personRepository.save(entity);

            EngineWriteRecord record = tracker.record(
                    PersonUpsertService.ENTITY_TYPE_PERSON, sourcePersonId, changedFields, runId);

            ObjectNode payload = buildStateChangedPayload(
                    changedFields,
                    extractFields(oldDetails, changedFields),
                    extractFields(mergedDetails, changedFields));

            handleEngineEventEmission(sourcePersonId, payload, record, runId);
        });

        return fubCall.get();
    }

    @Override
    public <T> T applyEntityAppendTrackedOnly(
            String sourcePersonId,
            String fieldName,
            Long runId,
            Supplier<T> fubCall) {

        validateSourcePersonId(sourcePersonId);
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must be non-blank");
        }

        T result = fubCall.get();

        requiresNewTx.executeWithoutResult(status ->
                tracker.record(
                        PersonUpsertService.ENTITY_TYPE_PERSON, sourcePersonId,
                        Set.of(fieldName), runId));
        return result;
    }

    @Override
    public <T> T applyEntityCreateTrackedOnly(
            String entityType,
            String sourcePersonId,
            Long runId,
            Supplier<T> fubCall,
            SideEffectRecorder<T> recordSideEffects) {

        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType must be non-blank");
        }
        validateSourcePersonId(sourcePersonId);
        if (recordSideEffects == null) {
            throw new IllegalArgumentException("recordSideEffects must be non-null");
        }

        T result = fubCall.get();

        requiresNewTx.executeWithoutResult(status -> {
            RecordContext ctx = new RecordContext(entityType, sourcePersonId, runId);
            recordSideEffects.record(tracker, result, ctx);
        });
        return result;
    }

    private static void validateSourcePersonId(String sourcePersonId) {
        if (sourcePersonId == null || sourcePersonId.isBlank()) {
            throw new IllegalArgumentException("sourcePersonId must be non-blank");
        }
    }

    private ObjectNode mergeFields(JsonNode oldDetails, Map<String, JsonNode> fieldUpdates) {
        ObjectNode merged = (oldDetails instanceof ObjectNode existing)
                ? existing.deepCopy()
                : objectMapper.createObjectNode();
        fieldUpdates.forEach(merged::set);
        return merged;
    }

    private ObjectNode extractFields(JsonNode details, Set<String> fields) {
        ObjectNode out = objectMapper.createObjectNode();
        if (details == null || details.isMissingNode() || details.isNull()) {
            return out;
        }
        for (String field : fields) {
            JsonNode value = details.get(field);
            if (value != null) {
                out.set(field, value);
            }
        }
        return out;
    }

    private ObjectNode buildStateChangedPayload(
            Set<String> changedFields, ObjectNode previous, ObjectNode current) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("changed_fields", objectMapper.valueToTree(changedFields));
        payload.set("previous", previous);
        payload.set("current", current);
        return payload;
    }

    private void handleEngineEventEmission(
            String sourcePersonId, ObjectNode payload, EngineWriteRecord trackerRecord, Long runId) {
        if (emitEvents) {
            domainEventEmitter.emit(
                    EVENT_KIND_PERSON_STATE_CHANGED,
                    PersonUpsertService.SOURCE_SYSTEM_FUB,
                    null,
                    PersonUpsertService.ENTITY_TYPE_PERSON,
                    sourcePersonId,
                    payload);
            return;
        }
        log.info("[engine-write-emit:LOG_ONLY] entityId={} changedFields={} runId={} trackerRecordId={} payload={}",
                sourcePersonId, trackerRecord.changedFields(), runId, trackerRecord.id(), payload);
    }
}
