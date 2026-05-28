package com.fuba.automation_engine.service.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.controller.dto.PersonActivityEventResponse;
import com.fuba.automation_engine.controller.dto.PersonActivityKind;
import com.fuba.automation_engine.controller.dto.PersonFeedItemResponse;
import com.fuba.automation_engine.controller.dto.PersonFeedPageResponse;
import com.fuba.automation_engine.controller.dto.PersonLiveStatus;
import com.fuba.automation_engine.controller.dto.PersonRecentCallResponse;
import com.fuba.automation_engine.controller.dto.PersonRecentWebhookEventResponse;
import com.fuba.automation_engine.controller.dto.PersonRecentWorkflowRunResponse;
import com.fuba.automation_engine.controller.dto.PersonSummaryResponse;
import com.fuba.automation_engine.exception.person.InvalidPersonFeedQueryException;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.repository.PersonFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.PersonFeedReadRepository.PersonFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.PersonFeedReadRepository.PersonFeedRow;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
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
 * Orchestrates read-only person queries for the admin UI.
 *
 * <p>Two operations:
 * <ol>
 *   <li>{@link #list(PersonFeedQuery)} — cursor-paginated persons list.
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
public class PersonAdminQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    // KNOWN LIMITATION (Phase 1): the summary endpoint returns at most the top 10
    // rows per stream (processed calls / workflow runs / webhook events) and a
    // merged top-20 unified activity timeline. Persons with more history will have
    // older rows silently truncated — there is no "…and N more" indicator yet.
    // The intended fix is not to grow these caps but to cross-link out to the
    // existing list pages with a `sourcePersonId` filter (see phases.md Slice D
    // deferral note). Until the list endpoints accept that filter, the caps are
    // the authoritative slice of history exposed on the detail surface.
    // Touched by: PER_STREAM_TOP_N used in findTop10BySourcePersonId… repo methods
    // (derived-query method names encode the limit, so changing this constant
    // alone is NOT sufficient — the repo signatures must be renamed too).
    private static final int PER_STREAM_TOP_N = 10;
    private static final int UNIFIED_ACTIVITY_TOP_N = 20;
    private static final String DEFAULT_SOURCE_SYSTEM = "FUB";

    private static final Logger log = LoggerFactory.getLogger(PersonAdminQueryService.class);

    private final PersonFeedReadRepository feedReadRepository;
    private final PersonRepository personRepository;
    private final ProcessedCallRepository processedCallRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PersonFeedCursorCodec cursorCodec;
    private final PersonUpsertService personUpsertService;
    private final FollowUpBossClient followUpBossClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PersonAdminQueryService(
            PersonFeedReadRepository feedReadRepository,
            PersonRepository personRepository,
            ProcessedCallRepository processedCallRepository,
            WorkflowRunRepository workflowRunRepository,
            WebhookEventRepository webhookEventRepository,
            PersonFeedCursorCodec cursorCodec,
            PersonUpsertService personUpsertService,
            FollowUpBossClient followUpBossClient,
            ObjectMapper objectMapper,
            Clock clock) {
        this.feedReadRepository = feedReadRepository;
        this.personRepository = personRepository;
        this.processedCallRepository = processedCallRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.cursorCodec = cursorCodec;
        this.personUpsertService = personUpsertService;
        this.followUpBossClient = followUpBossClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PersonFeedPageResponse list(PersonFeedQuery query) {
        int limit = normalizeLimit(query.limit());
        validateRange(query.from(), query.to());
        String normalizedPrefix = normalizeString(query.sourcePersonIdPrefix());
        PersonFeedCursorCodec.Cursor cursor = cursorCodec.decode(query.cursor());

        List<PersonFeedItemResponse> items = feedReadRepository.fetch(new PersonFeedReadQuery(
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

    public Optional<PersonSummaryResponse> summary(String sourcePersonId, String sourceSystem, boolean includeLive) {
        if (sourcePersonId == null || sourcePersonId.isBlank()) {
            return Optional.empty();
        }
        String normalizedSource = normalizeSourceSystem(sourceSystem);

        Optional<PersonEntity> localOpt = personRepository.findBySourceSystemAndSourcePersonId(normalizedSource, sourcePersonId);
        if (localOpt.isEmpty()) {
            return Optional.empty();
        }
        PersonEntity local = localOpt.get();

        LiveRefreshOutcome liveOutcome = includeLive
                ? refreshFromFub(local, normalizedSource, sourcePersonId)
                : new LiveRefreshOutcome(local, null, PersonLiveStatus.LIVE_SKIPPED, null);

        PersonEntity refreshed = liveOutcome.person();
        List<PersonRecentCallResponse> recentCalls = processedCallRepository
                .findTop10BySourcePersonIdOrderByCallStartedAtDescIdDesc(sourcePersonId).stream()
                .map(this::toRecentCall)
                .toList();
        List<PersonRecentWorkflowRunResponse> recentRuns = workflowRunRepository
                .findTop10BySourcePersonIdOrderByCreatedAtDescIdDesc(sourcePersonId).stream()
                .map(this::toRecentWorkflowRun)
                .toList();
        List<PersonRecentWebhookEventResponse> recentWebhooks = webhookEventRepository
                .findTop10BySourcePersonIdOrderByReceivedAtDescIdDesc(sourcePersonId).stream()
                .map(this::toRecentWebhookEvent)
                .toList();

        List<PersonActivityEventResponse> activity = mergeActivity(recentCalls, recentRuns, recentWebhooks);

        return Optional.of(new PersonSummaryResponse(
                toFeedItem(refreshed),
                liveOutcome.livePerson(),
                liveOutcome.status(),
                liveOutcome.message(),
                activity,
                recentCalls,
                recentRuns,
                recentWebhooks));
    }

    private LiveRefreshOutcome refreshFromFub(PersonEntity local, String sourceSystem, String sourcePersonId) {
        if (!"FUB".equals(sourceSystem)) {
            // Only FUB has a live fetch path today. Other sources just return local.
            return new LiveRefreshOutcome(local, null, PersonLiveStatus.LIVE_SKIPPED, null);
        }
        long personId;
        try {
            personId = Long.parseLong(sourcePersonId);
        } catch (NumberFormatException ex) {
            log.warn("Live refresh skipped — sourcePersonId is not numeric: {}", sourcePersonId);
            return new LiveRefreshOutcome(local, null, PersonLiveStatus.LIVE_FAILED, "sourcePersonId is not a numeric FUB person id");
        }
        try {
            JsonNode livePerson = followUpBossClient.getPersonRawById(personId);
            if (livePerson == null || livePerson.isNull()) {
                return new LiveRefreshOutcome(local, null, PersonLiveStatus.LIVE_FAILED, "FUB returned no person payload");
            }
            PersonEntity refreshed = personUpsertService.upsertFubPerson(sourcePersonId, livePerson);
            return new LiveRefreshOutcome(refreshed, livePerson, PersonLiveStatus.LIVE_OK, null);
        } catch (Exception ex) {
            log.warn("Live FUB refresh failed for sourcePersonId={}: {}", sourcePersonId, ex.getMessage());
            return new LiveRefreshOutcome(local, null, PersonLiveStatus.LIVE_FAILED, describeLiveFailure(ex));
        }
    }

    private String describeLiveFailure(Exception ex) {
        String msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : msg;
    }

    private List<PersonActivityEventResponse> mergeActivity(
            List<PersonRecentCallResponse> recentCalls,
            List<PersonRecentWorkflowRunResponse> recentRuns,
            List<PersonRecentWebhookEventResponse> recentWebhooks) {
        Stream<PersonActivityEventResponse> callEvents = recentCalls.stream().map(row ->
                new PersonActivityEventResponse(
                        PersonActivityKind.PROCESSED_CALL,
                        row.id(),
                        row.callStartedAt(),
                        describeCall(row),
                        row.status() == null ? null : row.status().name()));
        Stream<PersonActivityEventResponse> runEvents = recentRuns.stream().map(row ->
                new PersonActivityEventResponse(
                        PersonActivityKind.WORKFLOW_RUN,
                        row.id(),
                        row.createdAt(),
                        describeRun(row),
                        row.status() == null ? null : row.status().name()));
        Stream<PersonActivityEventResponse> webhookEvents = recentWebhooks.stream().map(row ->
                new PersonActivityEventResponse(
                        PersonActivityKind.WEBHOOK_EVENT,
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
    private Comparator<PersonActivityEventResponse> activityComparator() {
        return Comparator.comparing(PersonActivityEventResponse::occurredAt, Comparator.reverseOrder())
                .thenComparing(e -> e.refId() == null ? Long.MIN_VALUE : e.refId(), Comparator.reverseOrder());
    }

    private String describeCall(PersonRecentCallResponse row) {
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

    private String describeRun(PersonRecentWorkflowRunResponse row) {
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

    private String describeWebhook(PersonRecentWebhookEventResponse row) {
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

    private PersonFeedItemResponse toFeedItem(PersonFeedRow row) {
        return new PersonFeedItemResponse(
                row.id(),
                row.sourceSystem(),
                row.sourcePersonId(),
                row.status(),
                normalizeJson(row.personDetails()),
                row.createdAt(),
                row.updatedAt(),
                row.lastSyncedAt());
    }

    private PersonFeedItemResponse toFeedItem(PersonEntity entity) {
        return new PersonFeedItemResponse(
                entity.getId(),
                entity.getSourceSystem(),
                entity.getSourcePersonId(),
                entity.getStatus(),
                normalizeJson(entity.getPersonDetails()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastSyncedAt());
    }

    private PersonRecentCallResponse toRecentCall(ProcessedCallEntity entity) {
        return new PersonRecentCallResponse(
                entity.getId(),
                entity.getCallId(),
                entity.getStatus(),
                entity.getOutcome(),
                entity.getIsIncoming(),
                entity.getDurationSeconds(),
                entity.getCallStartedAt());
    }

    private PersonRecentWorkflowRunResponse toRecentWorkflowRun(WorkflowRunEntity entity) {
        return new PersonRecentWorkflowRunResponse(
                entity.getId(),
                entity.getWorkflowKey(),
                entity.getWorkflowVersion(),
                entity.getStatus(),
                entity.getReasonCode(),
                entity.getCreatedAt());
    }

    private PersonRecentWebhookEventResponse toRecentWebhookEvent(WebhookEventEntity entity) {
        return new PersonRecentWebhookEventResponse(
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

    private PersonFeedPageResponse buildPage(List<PersonFeedItemResponse> rows, int limit) {
        boolean hasNext = rows.size() > limit;
        List<PersonFeedItemResponse> pageItems = hasNext ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            PersonFeedItemResponse tail = pageItems.get(pageItems.size() - 1);
            nextCursor = cursorCodec.encode(tail.updatedAt(), tail.id());
        }
        return new PersonFeedPageResponse(
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
            throw new InvalidPersonFeedQueryException("'from' must be before or equal to 'to'");
        }
    }

    /**
     * Unsupported: not used yet. Kept private so callers go through the
     * typed record API.
     */
    @SuppressWarnings("unused")
    private static final List<String> UNUSED_MARKER = Collections.emptyList();

    public record PersonFeedQuery(
            String sourceSystem,
            PersonStatus status,
            String sourcePersonIdPrefix,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit,
            String cursor) {
    }

    private record LiveRefreshOutcome(
            PersonEntity person,
            JsonNode livePerson,
            PersonLiveStatus status,
            String message) {
    }
}
