package com.fuba.automation_engine.service.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.controller.dto.LeadActivityEventResponse;
import com.fuba.automation_engine.controller.dto.LeadActivityKind;
import com.fuba.automation_engine.controller.dto.LeadFeedItemResponse;
import com.fuba.automation_engine.controller.dto.LeadFeedPageResponse;
import com.fuba.automation_engine.controller.dto.LeadLiveStatus;
import com.fuba.automation_engine.controller.dto.LeadRecentCallResponse;
import com.fuba.automation_engine.controller.dto.LeadRecentWebhookEventResponse;
import com.fuba.automation_engine.controller.dto.LeadRecentWorkflowRunResponse;
import com.fuba.automation_engine.controller.dto.LeadSummaryResponse;
import com.fuba.automation_engine.exception.lead.InvalidLeadFeedQueryException;
import com.fuba.automation_engine.persistence.entity.LeadEntity;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository.LeadFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository.LeadFeedRow;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates read-only lead queries for the admin UI.
 *
 * <p>Two operations:
 * <ol>
 *   <li>{@link #list(LeadFeedQuery)} — cursor-paginated leads list.
 *   <li>{@link #summary(String, String, boolean)} — single-call aggregation for
 *       the detail surface: local row + optional live FUB refresh + per-stream
 *       top-10 recent rows + unified top-20 chronological activity timeline.
 * </ol>
 *
 * <p>Live refresh behaviour (per plan.md §Failure behavior + phases.md §C2):
 * when {@code includeLive=true} and the FUB call fails, the method returns
 * HTTP 200 with {@code liveStatus=LIVE_FAILED} and the local snapshot. It
 * never propagates the FUB error upward; operators can still triage from
 * local data.
 */
@Service
public class LeadAdminQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    // KNOWN LIMITATION (Phase 1): the summary endpoint returns at most the top 10
    // rows per stream (processed calls / workflow runs / webhook events) and a
    // merged top-20 unified activity timeline. Leads with more history will have
    // older rows silently truncated — there is no "…and N more" indicator yet.
    // The intended fix is not to grow these caps but to cross-link out to the
    // existing list pages with a `sourceLeadId` filter (see phases.md Slice D
    // deferral note). Until the list endpoints accept that filter, the caps are
    // the authoritative slice of history exposed on the detail surface.
    // Touched by: PER_STREAM_TOP_N used in findTop10BySourceLeadId… repo methods
    // (derived-query method names encode the limit, so changing this constant
    // alone is NOT sufficient — the repo signatures must be renamed too).
    private static final int PER_STREAM_TOP_N = 10;
    private static final int UNIFIED_ACTIVITY_TOP_N = 20;
    private static final String DEFAULT_SOURCE_SYSTEM = "FUB";

    private static final Logger log = LoggerFactory.getLogger(LeadAdminQueryService.class);

    private final LeadFeedReadRepository feedReadRepository;
    private final LeadRepository leadRepository;
    private final ProcessedCallRepository processedCallRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final LeadFeedCursorCodec cursorCodec;
    private final LeadUpsertService leadUpsertService;
    private final FollowUpBossClient followUpBossClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LeadAdminQueryService(
            LeadFeedReadRepository feedReadRepository,
            LeadRepository leadRepository,
            ProcessedCallRepository processedCallRepository,
            WorkflowRunRepository workflowRunRepository,
            WebhookEventRepository webhookEventRepository,
            LeadFeedCursorCodec cursorCodec,
            LeadUpsertService leadUpsertService,
            FollowUpBossClient followUpBossClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.feedReadRepository = feedReadRepository;
        this.leadRepository = leadRepository;
        this.processedCallRepository = processedCallRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.cursorCodec = cursorCodec;
        this.leadUpsertService = leadUpsertService;
        this.followUpBossClient = followUpBossClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public LeadFeedPageResponse list(LeadFeedQuery query) {
        int limit = normalizeLimit(query.limit());
        validateRange(query.from(), query.to());
        String normalizedPrefix = normalizeString(query.sourceLeadIdPrefix());
        LeadFeedCursorCodec.Cursor cursor = cursorCodec.decode(query.cursor());

        List<LeadFeedItemResponse> items = feedReadRepository.fetch(new LeadFeedReadQuery(
                        normalizeSourceSystem(query.sourceSystem()),
                        query.status(),
                        normalizedPrefix,
                        query.from(),
                        query.to(),
                        cursor.updatedAt(),
                        cursor.id(),
                        limit + 1))
                .stream()
                .map(this::toFeedItem)
                .toList();

        return buildPage(items, limit);
    }

    public Optional<LeadSummaryResponse> summary(String sourceLeadId, String sourceSystem, boolean includeLive) {
        if (sourceLeadId == null || sourceLeadId.isBlank()) {
            return Optional.empty();
        }
        String normalizedSource = normalizeSourceSystem(sourceSystem);

        Optional<LeadEntity> localOpt = leadRepository.findBySourceSystemAndSourceLeadId(normalizedSource, sourceLeadId);
        if (localOpt.isEmpty()) {
            return Optional.empty();
        }
        LeadEntity local = localOpt.get();

        LiveRefreshOutcome liveOutcome = includeLive
                ? refreshFromFub(local, normalizedSource, sourceLeadId)
                : new LiveRefreshOutcome(local, null, LeadLiveStatus.LIVE_SKIPPED, null);

        LeadEntity refreshed = liveOutcome.lead();
        List<LeadRecentCallResponse> recentCalls = processedCallRepository
                .findTop10BySourceLeadIdOrderByCallStartedAtDescIdDesc(sourceLeadId).stream()
                .map(this::toRecentCall)
                .toList();
        List<LeadRecentWorkflowRunResponse> recentRuns = workflowRunRepository
                .findTop10BySourceLeadIdOrderByCreatedAtDescIdDesc(sourceLeadId).stream()
                .map(this::toRecentWorkflowRun)
                .toList();
        List<LeadRecentWebhookEventResponse> recentWebhooks = webhookEventRepository
                .findTop10BySourceLeadIdOrderByReceivedAtDescIdDesc(sourceLeadId).stream()
                .map(this::toRecentWebhookEvent)
                .toList();

        List<LeadActivityEventResponse> activity = mergeActivity(recentCalls, recentRuns, recentWebhooks);

        return Optional.of(new LeadSummaryResponse(
                toFeedItem(refreshed),
                liveOutcome.livePerson(),
                liveOutcome.status(),
                liveOutcome.message(),
                activity,
                recentCalls,
                recentRuns,
                recentWebhooks));
    }

    private LiveRefreshOutcome refreshFromFub(LeadEntity local, String sourceSystem, String sourceLeadId) {
        if (!"FUB".equals(sourceSystem)) {
            // Only FUB has a live fetch path today. Other sources just return local.
            return new LiveRefreshOutcome(local, null, LeadLiveStatus.LIVE_SKIPPED, null);
        }
        long personId;
        try {
            personId = Long.parseLong(sourceLeadId);
        } catch (NumberFormatException ex) {
            log.warn("Live refresh skipped — sourceLeadId is not numeric: {}", sourceLeadId);
            return new LiveRefreshOutcome(local, null, LeadLiveStatus.LIVE_FAILED, "sourceLeadId is not a numeric FUB person id");
        }
        try {
            JsonNode livePerson = followUpBossClient.getPersonRawById(personId);
            if (livePerson == null || livePerson.isNull()) {
                return new LiveRefreshOutcome(local, null, LeadLiveStatus.LIVE_FAILED, "FUB returned no person payload");
            }
            LeadEntity refreshed = leadUpsertService.upsertFubPerson(sourceLeadId, livePerson);
            return new LiveRefreshOutcome(refreshed, livePerson, LeadLiveStatus.LIVE_OK, null);
        } catch (Exception ex) {
            log.warn("Live FUB refresh failed for sourceLeadId={}: {}", sourceLeadId, ex.getMessage());
            return new LiveRefreshOutcome(local, null, LeadLiveStatus.LIVE_FAILED, describeLiveFailure(ex));
        }
    }

    private String describeLiveFailure(Exception ex) {
        String msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
    }

    private List<LeadActivityEventResponse> mergeActivity(
            List<LeadRecentCallResponse> recentCalls,
            List<LeadRecentWorkflowRunResponse> recentRuns,
            List<LeadRecentWebhookEventResponse> recentWebhooks) {
        Stream<LeadActivityEventResponse> callEvents = recentCalls.stream().map(row ->
                new LeadActivityEventResponse(
                        LeadActivityKind.PROCESSED_CALL,
                        row.id(),
                        row.callStartedAt(),
                        describeCall(row),
                        row.status() == null ? null : row.status().name()));
        Stream<LeadActivityEventResponse> runEvents = recentRuns.stream().map(row ->
                new LeadActivityEventResponse(
                        LeadActivityKind.WORKFLOW_RUN,
                        row.id(),
                        row.createdAt(),
                        describeRun(row),
                        row.status() == null ? null : row.status().name()));
        Stream<LeadActivityEventResponse> webhookEvents = recentWebhooks.stream().map(row ->
                new LeadActivityEventResponse(
                        LeadActivityKind.WEBHOOK_EVENT,
                        row.id(),
                        row.receivedAt(),
                        describeWebhook(row),
                        row.status() == null ? null : row.status().name()));

        return Stream.concat(callEvents, Stream.concat(runEvents, webhookEvents))
                .filter(event -> event.occurredAt() != null)
                .sorted(activityComparator())
                .limit(UNIFIED_ACTIVITY_TOP_N)
                .toList();
    }

    /**
     * Order by occurredAt DESC; tiebreak by refId DESC within the same kind
     * to keep ordering deterministic when two events share a timestamp.
     */
    private Comparator<LeadActivityEventResponse> activityComparator() {
        return Comparator.comparing(LeadActivityEventResponse::occurredAt, Comparator.reverseOrder())
                .thenComparing(e -> e.refId() == null ? Long.MIN_VALUE : e.refId(), Comparator.reverseOrder());
    }

    private String describeCall(LeadRecentCallResponse row) {
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(row.isIncoming())) {
            sb.append("Inbound call");
        } else if (Boolean.FALSE.equals(row.isIncoming())) {
            sb.append("Outbound call");
        } else {
            sb.append("Call");
        }
        if (row.outcome() != null && !row.outcome().isBlank()) {
            sb.append(" · ").append(row.outcome());
        }
        if (row.durationSeconds() != null && row.durationSeconds() > 0) {
            sb.append(" · ").append(row.durationSeconds()).append("s");
        }
        return sb.toString();
    }

    private String describeRun(LeadRecentWorkflowRunResponse row) {
        StringBuilder sb = new StringBuilder();
        sb.append("Workflow ").append(row.workflowKey() == null ? "(unknown)" : row.workflowKey());
        if (row.workflowVersion() != null) {
            sb.append(" v").append(row.workflowVersion());
        }
        if (row.reasonCode() != null && !row.reasonCode().isBlank()) {
            sb.append(" · ").append(row.reasonCode());
        }
        return sb.toString();
    }

    private String describeWebhook(LeadRecentWebhookEventResponse row) {
        StringBuilder sb = new StringBuilder();
        if (row.source() != null) {
            sb.append(row.source().name()).append(" webhook");
        } else {
            sb.append("Webhook");
        }
        if (row.eventType() != null && !row.eventType().isBlank()) {
            sb.append(" · ").append(row.eventType());
        }
        return sb.toString();
    }

    private LeadFeedItemResponse toFeedItem(LeadFeedRow row) {
        return new LeadFeedItemResponse(
                row.id(),
                row.sourceSystem(),
                row.sourceLeadId(),
                row.status(),
                normalizeJson(row.leadDetails()),
                row.createdAt(),
                row.updatedAt(),
                row.lastSyncedAt());
    }

    private LeadFeedItemResponse toFeedItem(LeadEntity entity) {
        return new LeadFeedItemResponse(
                entity.getId(),
                entity.getSourceSystem(),
                entity.getSourceLeadId(),
                entity.getStatus(),
                normalizeJson(entity.getLeadDetails()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastSyncedAt());
    }

    private LeadRecentCallResponse toRecentCall(ProcessedCallEntity entity) {
        return new LeadRecentCallResponse(
                entity.getId(),
                entity.getCallId(),
                entity.getStatus(),
                entity.getOutcome(),
                entity.getIsIncoming(),
                entity.getDurationSeconds(),
                entity.getCallStartedAt());
    }

    private LeadRecentWorkflowRunResponse toRecentWorkflowRun(WorkflowRunEntity entity) {
        return new LeadRecentWorkflowRunResponse(
                entity.getId(),
                entity.getWorkflowKey(),
                entity.getWorkflowVersion(),
                entity.getStatus(),
                entity.getReasonCode(),
                entity.getCreatedAt());
    }

    private LeadRecentWebhookEventResponse toRecentWebhookEvent(WebhookEventEntity entity) {
        return new LeadRecentWebhookEventResponse(
                entity.getId(),
                entity.getSource(),
                entity.getEventType(),
                entity.getStatus(),
                entity.getReceivedAt());
    }

    private JsonNode normalizeJson(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payload.toString());
        } catch (Exception ex) {
            return payload;
        }
    }

    private LeadFeedPageResponse buildPage(List<LeadFeedItemResponse> rows, int limit) {
        boolean hasNext = rows.size() > limit;
        List<LeadFeedItemResponse> pageItems = hasNext ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            LeadFeedItemResponse tail = pageItems.get(pageItems.size() - 1);
            nextCursor = cursorCodec.encode(tail.updatedAt(), tail.id());
        }
        return new LeadFeedPageResponse(
                List.copyOf(pageItems),
                nextCursor,
                OffsetDateTime.now(clock));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            return MAX_LIMIT;
        }
        return limit;
    }

    private String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSourceSystem(String sourceSystem) {
        String trimmed = normalizeString(sourceSystem);
        return trimmed == null ? DEFAULT_SOURCE_SYSTEM : trimmed;
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidLeadFeedQueryException("'from' must be before or equal to 'to'");
        }
    }

    /**
     * Unsupported: not used yet. Kept private so callers go through the
     * typed record API.
     */
    @SuppressWarnings("unused")
    private static final List<String> UNUSED_MARKER = Collections.emptyList();

    public record LeadFeedQuery(
            String sourceSystem,
            LeadStatus status,
            String sourceLeadIdPrefix,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit,
            String cursor) {
    }

    private record LiveRefreshOutcome(
            LeadEntity lead,
            JsonNode livePerson,
            LeadLiveStatus status,
            String message) {
    }
}
