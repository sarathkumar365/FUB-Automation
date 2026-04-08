package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.controller.dto.PolicyExecutionRunDetailResponse;
import com.fuba.automation_engine.controller.dto.PolicyExecutionRunListItemResponse;
import com.fuba.automation_engine.controller.dto.PolicyExecutionRunPageResponse;
import com.fuba.automation_engine.controller.dto.PolicyExecutionStepResponse;
import com.fuba.automation_engine.exception.policy.InvalidPolicyExecutionQueryException;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class AdminPolicyExecutionService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final PolicyExecutionRunRepository runRepository;
    private final PolicyExecutionStepRepository stepRepository;
    private final PolicyExecutionCursorCodec cursorCodec;
    private final Clock clock;

    public AdminPolicyExecutionService(
            PolicyExecutionRunRepository runRepository,
            PolicyExecutionStepRepository stepRepository,
            PolicyExecutionCursorCodec cursorCodec,
            Clock clock) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    public PolicyExecutionRunPageResponse list(PolicyExecutionFeedQuery query) {
        int limit = normalizeLimit(query.limit());
        validateRange(query.from(), query.to());
        String normalizedPolicyKey = normalizePolicyKey(query.policyKey());
        PolicyExecutionCursorCodec.Cursor cursor = cursorCodec.decode(query.cursor());

        Specification<PolicyExecutionRunEntity> spec = buildFeedSpecification(
                query.status(),
                normalizedPolicyKey,
                query.from(),
                query.to(),
                cursor.createdAt(),
                cursor.id());
        Page<PolicyExecutionRunEntity> page = runRepository.findAll(
                spec,
                PageRequest.of(0, limit + 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<PolicyExecutionRunEntity> rows = page.getContent();

        boolean hasNext = rows.size() > limit;
        List<PolicyExecutionRunEntity> pageRows = hasNext ? rows.subList(0, limit) : rows;
        List<PolicyExecutionRunListItemResponse> items = pageRows.stream().map(this::toListItem).toList();

        String nextCursor = null;
        if (hasNext && !pageRows.isEmpty()) {
            PolicyExecutionRunEntity tail = pageRows.get(pageRows.size() - 1);
            nextCursor = cursorCodec.encode(tail.getCreatedAt(), tail.getId());
        }

        return new PolicyExecutionRunPageResponse(items, nextCursor, OffsetDateTime.now(clock));
    }

    public Optional<PolicyExecutionRunDetailResponse> findDetail(long runId) {
        return runRepository.findById(runId).map(this::toDetail);
    }

    private PolicyExecutionRunDetailResponse toDetail(PolicyExecutionRunEntity run) {
        List<PolicyExecutionStepResponse> steps = stepRepository.findByRunIdOrderByStepOrderAsc(run.getId())
                .stream()
                .map(this::toStepResponse)
                .toList();

        return new PolicyExecutionRunDetailResponse(
                run.getId(),
                run.getSource(),
                run.getEventId(),
                run.getWebhookEventId(),
                run.getSourceLeadId(),
                run.getDomain(),
                run.getPolicyKey(),
                run.getPolicyVersion(),
                run.getPolicyBlueprintSnapshot(),
                run.getStatus(),
                run.getReasonCode(),
                run.getIdempotencyKey(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                steps);
    }

    private PolicyExecutionRunListItemResponse toListItem(PolicyExecutionRunEntity run) {
        return new PolicyExecutionRunListItemResponse(
                run.getId(),
                run.getSource(),
                run.getEventId(),
                run.getSourceLeadId(),
                run.getDomain(),
                run.getPolicyKey(),
                run.getPolicyVersion(),
                run.getStatus(),
                run.getReasonCode(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

    private PolicyExecutionStepResponse toStepResponse(PolicyExecutionStepEntity step) {
        return new PolicyExecutionStepResponse(
                step.getId(),
                step.getStepOrder(),
                step.getStepType(),
                step.getStatus(),
                step.getDueAt(),
                step.getDependsOnStepOrder(),
                step.getResultCode(),
                step.getErrorMessage(),
                step.getCreatedAt(),
                step.getUpdatedAt());
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

    private String normalizePolicyKey(String policyKey) {
        if (policyKey == null || policyKey.isBlank()) {
            return null;
        }
        return policyKey.trim().toUpperCase(Locale.ROOT);
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidPolicyExecutionQueryException("'from' must be before or equal to 'to'");
        }
    }

    private Specification<PolicyExecutionRunEntity> buildFeedSpecification(
            PolicyExecutionRunStatus status,
            String policyKey,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorCreatedAt,
            Long cursorId) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (policyKey != null) {
                predicates.add(criteriaBuilder.equal(root.get("policyKey"), policyKey));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (cursorCreatedAt != null && cursorId != null) {
                jakarta.persistence.criteria.Predicate olderCreatedAt =
                        criteriaBuilder.lessThan(root.get("createdAt"), cursorCreatedAt);
                jakarta.persistence.criteria.Predicate sameCreatedAtOlderId = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("createdAt"), cursorCreatedAt),
                        criteriaBuilder.lessThan(root.get("id"), cursorId));
                predicates.add(criteriaBuilder.or(olderCreatedAt, sameCreatedAtOlderId));
            }

            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public record PolicyExecutionFeedQuery(
            PolicyExecutionRunStatus status,
            String policyKey,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit,
            String cursor) {
    }
}
