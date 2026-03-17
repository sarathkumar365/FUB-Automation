package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.webhook.dispatch.WebhookDispatcher;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ProcessedCallAdminService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ProcessedCallRepository processedCallRepository;
    private final WebhookDispatcher webhookDispatcher;
    private final ObjectMapper objectMapper;

    public ProcessedCallAdminService(
            ProcessedCallRepository processedCallRepository,
            WebhookDispatcher webhookDispatcher,
            ObjectMapper objectMapper) {
        this.processedCallRepository = processedCallRepository;
        this.webhookDispatcher = webhookDispatcher;
        this.objectMapper = objectMapper;
    }

    public List<ProcessedCallEntity> list(ProcessedCallStatus status, OffsetDateTime from, OffsetDateTime to, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        return processedCallRepository.findForAdmin(status, from, to, PageRequest.of(0, normalizedLimit));
    }

    public ReplayOutcome replay(long callId) {
        Optional<ProcessedCallEntity> existing = processedCallRepository.findByCallId(callId);
        if (existing.isEmpty()) {
            return ReplayOutcome.NOT_FOUND;
        }

        ProcessedCallEntity entity = existing.get();
        if (entity.getStatus() != ProcessedCallStatus.FAILED) {
            return ReplayOutcome.NOT_REPLAYABLE;
        }

        entity.setStatus(ProcessedCallStatus.RECEIVED);
        entity.setFailureReason(null);
        entity.setRuleApplied(null);
        entity.setTaskId(null);
        // TODO(step5-admin): reset retryCount on replay so each replay starts with a clean retry history.
        entity.setUpdatedAt(OffsetDateTime.now());
        processedCallRepository.save(entity);

        webhookDispatcher.dispatch(new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "replay-" + callId + "-" + System.currentTimeMillis(),
                WebhookEventStatus.RECEIVED,
                buildReplayPayload(entity),
                OffsetDateTime.now(),
                null));
        return ReplayOutcome.ACCEPTED;
    }

    private JsonNode buildReplayPayload(ProcessedCallEntity entity) {
        ObjectNode payload = objectMapper.createObjectNode();
        String eventType = "callsCreated";
        if (entity.getRawPayload() != null && entity.getRawPayload().has("eventType")) {
            eventType = entity.getRawPayload().get("eventType").asText("callsCreated");
        }
        payload.put("eventType", eventType);
        ArrayNode resourceIds = payload.putArray("resourceIds");
        resourceIds.add(entity.getCallId());
        payload.putNull("uri");
        return payload;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public enum ReplayOutcome {
        ACCEPTED,
        NOT_FOUND,
        NOT_REPLAYABLE
    }
}
