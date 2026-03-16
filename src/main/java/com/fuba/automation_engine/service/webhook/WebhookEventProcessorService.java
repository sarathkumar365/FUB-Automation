package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.rules.CallDecision;
import com.fuba.automation_engine.rules.CallDecisionAction;
import com.fuba.automation_engine.rules.CallDecisionEngine;
import com.fuba.automation_engine.rules.CallPreValidationService;
import com.fuba.automation_engine.rules.CallbackTaskCommandFactory;
import com.fuba.automation_engine.rules.PreValidationResult;
import com.fuba.automation_engine.rules.ValidatedCallContext;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
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
    private static final String TRANSIENT_FETCH_FAILURE = "TRANSIENT_FETCH_FAILURE";
    private static final String PERMANENT_FETCH_FAILURE = "PERMANENT_FETCH_FAILURE";
    private static final String UNEXPECTED_PROCESSING_FAILURE = "UNEXPECTED_PROCESSING_FAILURE";
    private static final String TRANSIENT_TASK_CREATE_FAILURE = "TRANSIENT_TASK_CREATE_FAILURE";
    private static final String PERMANENT_TASK_CREATE_FAILURE = "PERMANENT_TASK_CREATE_FAILURE";
    private static final String UNEXPECTED_TASK_CREATE_FAILURE = "UNEXPECTED_TASK_CREATE_FAILURE";

    private final ProcessedCallRepository processedCallRepository;
    private final FollowUpBossClient followUpBossClient;
    private final CallPreValidationService callPreValidationService;
    private final CallDecisionEngine callDecisionEngine;
    private final CallbackTaskCommandFactory callbackTaskCommandFactory;

    public WebhookEventProcessorService(
            ProcessedCallRepository processedCallRepository,
            FollowUpBossClient followUpBossClient,
            CallPreValidationService callPreValidationService,
            CallDecisionEngine callDecisionEngine,
            CallbackTaskCommandFactory callbackTaskCommandFactory) {
        this.processedCallRepository = processedCallRepository;
        this.followUpBossClient = followUpBossClient;
        this.callPreValidationService = callPreValidationService;
        this.callDecisionEngine = callDecisionEngine;
        this.callbackTaskCommandFactory = callbackTaskCommandFactory;
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
            CallDetails callDetails = followUpBossClient.getCallById(callId);
            log.info("Fetched call details from FUB callId={}", callId);
            Optional<PreValidationResult> preValidationResult = callPreValidationService.validate(callDetails);
            if (preValidationResult.isPresent()) {
                handlePreValidationTerminal(entity, callDetails, preValidationResult.get());
                return;
            }

            ValidatedCallContext callContext = callPreValidationService.normalize(callDetails);
            CallDecision decision = callDecisionEngine.decide(callContext);
            executeDecision(entity, callContext, decision);
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

    private void handlePreValidationTerminal(ProcessedCallEntity entity, CallDetails callDetails, PreValidationResult preValidationResult) {
        if (preValidationResult.action() == CallDecisionAction.SKIP) {
            log.warn(
                    "Pre-validation skip callId={} reason={} userId={}",
                    entity.getCallId(),
                    preValidationResult.reasonCode(),
                    callDetails.userId());
            markSkipped(entity, preValidationResult.reasonCode());
            return;
        }

        log.warn(
                "Pre-validation failure callId={} reason={} duration={}",
                entity.getCallId(),
                preValidationResult.reasonCode(),
                callDetails.duration());
        markFailed(entity, preValidationResult.reasonCode());
    }

    private void executeDecision(ProcessedCallEntity entity, ValidatedCallContext callContext, CallDecision decision) {
        if (decision.action() == CallDecisionAction.SKIP) {
            log.warn(
                    "Skipping task creation callId={} reason={} userId={} duration={}",
                    entity.getCallId(),
                    decision.reasonCode(),
                    callContext.userId(),
                    callContext.duration());
            markSkipped(entity, decision.reasonCode());
            return;
        }

        if (decision.action() == CallDecisionAction.FAIL) {
            log.warn(
                    "Failing call processing callId={} reason={} outcome={} duration={}",
                    entity.getCallId(),
                    decision.reasonCode(),
                    callContext.normalizedOutcome(),
                    callContext.duration());
            markFailed(entity, decision.reasonCode());
            return;
        }

        try {
            CreateTaskCommand command = callbackTaskCommandFactory.fromDecision(decision, callContext);
            CreatedTask task = followUpBossClient.createTask(command);
            markTaskCreated(entity, decision.ruleApplied(), task.id());
        } catch (FubTransientException ex) {
            log.warn("Transient FUB task create failure callId={} status={}", entity.getCallId(), stringifyStatus(ex.getStatusCode()));
            markFailed(entity, TRANSIENT_TASK_CREATE_FAILURE + ":" + stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            log.warn("Permanent FUB task create failure callId={} status={}", entity.getCallId(), stringifyStatus(ex.getStatusCode()));
            markFailed(entity, PERMANENT_TASK_CREATE_FAILURE + ":" + stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error("Unexpected task create failure callId={}", entity.getCallId(), ex);
            markFailed(entity, UNEXPECTED_TASK_CREATE_FAILURE);
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
        entity.setRuleApplied(null);
        entity.setTaskId(null);
        entity.setFailureReason(reason);
        entity.setUpdatedAt(OffsetDateTime.now());
        processedCallRepository.save(entity);
        log.info("Call marked FAILED callId={} reason={}", entity.getCallId(), reason);
    }

    private void markSkipped(ProcessedCallEntity entity, String reason) {
        entity.setStatus(ProcessedCallStatus.SKIPPED);
        entity.setRuleApplied(null);
        entity.setTaskId(null);
        entity.setFailureReason(reason);
        entity.setUpdatedAt(OffsetDateTime.now());
        processedCallRepository.save(entity);
        log.info("Call marked SKIPPED callId={} reason={}", entity.getCallId(), reason);
    }

    private void markTaskCreated(ProcessedCallEntity entity, String ruleApplied, Long taskId) {
        entity.setStatus(ProcessedCallStatus.TASK_CREATED);
        entity.setRuleApplied(ruleApplied);
        entity.setTaskId(taskId);
        entity.setFailureReason(null);
        entity.setUpdatedAt(OffsetDateTime.now());
        processedCallRepository.save(entity);
        log.info("Task created for call callId={} taskId={} rule={}", entity.getCallId(), taskId, ruleApplied);
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
