package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class WebhookEventProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventProcessorService.class);
    private static final String EVENT_CALLS_CREATED = "callsCreated";
    private static final String EVENT_TYPE_NOT_SUPPORTED = "EVENT_TYPE_NOT_SUPPORTED_IN_STEP3";
    private static final String RULE_ENGINE_PENDING = "RULE_ENGINE_PENDING_STEP4";
    private static final String TRANSIENT_FETCH_FAILURE = "TRANSIENT_FETCH_FAILURE";
    private static final String PERMANENT_FETCH_FAILURE = "PERMANENT_FETCH_FAILURE";
    private static final String UNEXPECTED_PROCESSING_FAILURE = "UNEXPECTED_PROCESSING_FAILURE";

    private final ProcessedCallRepository processedCallRepository;
    private final FollowUpBossClient followUpBossClient;

    public WebhookEventProcessorService(
            ProcessedCallRepository processedCallRepository,
            FollowUpBossClient followUpBossClient) {
        this.processedCallRepository = processedCallRepository;
        this.followUpBossClient = followUpBossClient;
    }

    public void process(NormalizedWebhookEvent event) {
        String eventType = extractEventType(event.payload());
        List<Long> callIds = extractResourceIds(event.payload());
        log.info(
                "Processing webhook event eventId={} source={} eventType={} callIdCount={}",
                event.eventId(),
                event.source(),
                eventType,
                callIds.size());
        if (callIds.isEmpty()) {
            log.info("No resourceIds present for eventId={}, eventType={}", event.eventId(), eventType);
            return;
        }

        boolean supportedEventType = EVENT_CALLS_CREATED.equals(eventType);
        for (Long callId : callIds) {
            processCall(event, eventType, callId, supportedEventType);
        }
    }

    private void processCall(NormalizedWebhookEvent event, String eventType, Long callId, boolean supportedEventType) {
        ProcessedCallEntity entity = getOrCreateEntity(callId, event.payload());
        // TODO(step3-concurrency): this check + state update is not an atomic claim.
        // Duplicate deliveries can run on separate async workers and both pass this branch,
        // so downstream side effects (for example Follow Up Boss reads/writes) may run twice.
        // Replace with a single DB claim transition (for example RECEIVED/RETRYABLE -> PROCESSING)
        // that only one worker can win.
        if (isTerminal(entity.getStatus())) {
            log.info("Skipping processing for terminal call state callId={} status={}", callId, entity.getStatus());
            return;
        }

        setStatus(entity, ProcessedCallStatus.PROCESSING);
        log.info("Call moved to PROCESSING callId={} eventId={} eventType={}", callId, event.eventId(), eventType);

        if (!supportedEventType) {
            markFailed(entity, EVENT_TYPE_NOT_SUPPORTED + ":" + eventType);
            return;
        }

        try {
            followUpBossClient.getCallById(callId);
            log.info("Fetched call details from FUB callId={}", callId);
            markFailed(entity, RULE_ENGINE_PENDING);
        } catch (FubTransientException ex) {
            log.warn("Transient FUB fetch failure callId={} status={}", callId, stringifyStatus(ex.getStatusCode()));
            markFailed(entity, TRANSIENT_FETCH_FAILURE + ":" + stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            log.warn("Permanent FUB fetch failure callId={} status={}", callId, stringifyStatus(ex.getStatusCode()));
            markFailed(entity, PERMANENT_FETCH_FAILURE + ":" + stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error("Unexpected processing failure callId={}", callId, ex);
            markFailed(entity, UNEXPECTED_PROCESSING_FAILURE);
        }
    }

    private ProcessedCallEntity getOrCreateEntity(Long callId, JsonNode rawPayload) {
        Optional<ProcessedCallEntity> existing = processedCallRepository.findByCallId(callId);
        if (existing.isPresent()) {
            return existing.get();
        }

        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setCallId(callId);
        entity.setStatus(ProcessedCallStatus.RECEIVED);
        entity.setRawPayload(rawPayload);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setRetryCount(0);
        try {
            return processedCallRepository.save(entity);
        } catch (DataIntegrityViolationException ignored) {
            log.info("Processed call row already exists callId={}", callId);
            return processedCallRepository.findByCallId(callId)
                    .orElseThrow(() -> new IllegalStateException("Unable to recover existing processed call for callId=" + callId));
        }
    }

    private void setStatus(ProcessedCallEntity entity, ProcessedCallStatus status) {
        entity.setStatus(status);
        entity.setUpdatedAt(OffsetDateTime.now());
        processedCallRepository.save(entity);
    }

    private void markFailed(ProcessedCallEntity entity, String reason) {
        entity.setStatus(ProcessedCallStatus.FAILED);
        entity.setFailureReason(reason);
        entity.setUpdatedAt(OffsetDateTime.now());
        processedCallRepository.save(entity);
        log.info("Call marked FAILED callId={} reason={}", entity.getCallId(), reason);
    }

    private boolean isTerminal(ProcessedCallStatus status) {
        return status == ProcessedCallStatus.FAILED
                || status == ProcessedCallStatus.SKIPPED
                || status == ProcessedCallStatus.TASK_CREATED;
    }

    private String extractEventType(JsonNode payload) {
        JsonNode eventTypeNode = payload == null ? null : payload.get("eventType");
        return eventTypeNode == null || eventTypeNode.isNull() ? "" : eventTypeNode.asText("");
    }

    private List<Long> extractResourceIds(JsonNode payload) {
        List<Long> result = new ArrayList<>();
        JsonNode idsNode = payload == null ? null : payload.get("resourceIds");
        if (idsNode == null || !idsNode.isArray()) {
            return result;
        }
        for (JsonNode node : idsNode) {
            if (node != null && node.canConvertToLong()) {
                result.add(node.asLong());
            }
        }
        return result;
    }

    private String stringifyStatus(Integer statusCode) {
        return statusCode == null ? "N/A" : statusCode.toString();
    }
}
