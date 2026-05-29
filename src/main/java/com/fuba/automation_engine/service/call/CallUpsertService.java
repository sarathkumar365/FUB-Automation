package com.fuba.automation_engine.service.call;

import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
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

    private static final Logger log = LoggerFactory.getLogger(CallUpsertService.class);

    private final ProcessedCallRepository processedCallRepository;
    private final PersonRepository personRepository;

    public CallUpsertService(ProcessedCallRepository processedCallRepository, PersonRepository personRepository) {
        this.processedCallRepository = processedCallRepository;
        this.personRepository = personRepository;
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
}
