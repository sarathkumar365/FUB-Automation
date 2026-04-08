package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.LookupResult;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.PolicyView;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(PolicyExecutionManager.class);
    private static final String POLICY_NOT_FOUND = "POLICY_NOT_FOUND";
    private static final String POLICY_INVALID = "POLICY_INVALID";
    private static final String POLICY_LOOKUP_INVALID_INPUT = "POLICY_LOOKUP_INVALID_INPUT";
    private static final String DUPLICATE_IDEMPOTENCY_KEY = "DUPLICATE_IDEMPOTENCY_KEY";
    private static final String IDEMPOTENCY_CONSTRAINT = "uk_policy_execution_runs_idempotency_key";
    private static final String IDEMPOTENCY_KEY_PREFIX = "PEM1|";

    private final AutomationPolicyService automationPolicyService;
    private final PolicyExecutionRunRepository runRepository;
    private final PolicyExecutionStepRepository stepRepository;
    private final EntityManager entityManager;

    public PolicyExecutionManager(
            AutomationPolicyService automationPolicyService,
            PolicyExecutionRunRepository runRepository,
            PolicyExecutionStepRepository stepRepository,
            EntityManager entityManager) {
        this.automationPolicyService = automationPolicyService;
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public PolicyExecutionPlanningResult plan(PolicyExecutionPlanRequest request) {
        if (request == null) {
            return new PolicyExecutionPlanningResult(PolicyExecutionRunStatus.BLOCKED_POLICY, null, POLICY_LOOKUP_INVALID_INPUT);
        }

        String idempotencyKey = buildIdempotencyKey(request);
        Optional<PolicyExecutionRunEntity> existingByKey = runRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return duplicateResult(existingByKey.get());
        }

        LookupResult policyLookup = automationPolicyService.getActivePolicy(request.policyDomain(), request.policyKey());
        if (policyLookup.status() != AutomationPolicyService.ReadStatus.SUCCESS || policyLookup.policy() == null) {
            return persistBlockedPolicyRun(request, idempotencyKey, policyLookup.status());
        }

        PolicyView policy = policyLookup.policy();
        // Identity resolver is intentionally removed for current assignment flow.
        // sourceLeadId remains the execution identity for policy runs in this phase.

        OffsetDateTime now = OffsetDateTime.now();
        PolicyExecutionRunEntity run = new PolicyExecutionRunEntity();
        run.setSource(request.sourceSystem());
        run.setEventId(request.eventId());
        run.setWebhookEventId(request.webhookEventId());
        run.setSourceLeadId(request.sourceLeadId());
        run.setDomain(normalize(request.policyDomain()));
        run.setPolicyKey(normalize(request.policyKey()));
        run.setPolicyVersion(policy.version() == null ? 0L : policy.version());
        run.setPolicyBlueprintSnapshot(policy.blueprint() == null ? Map.of() : policy.blueprint());
        run.setStatus(PolicyExecutionRunStatus.PENDING);
        run.setReasonCode(null);
        run.setIdempotencyKey(idempotencyKey);

        try {
            PolicyExecutionRunEntity savedRun = runRepository.saveAndFlush(run);
            persistInitialSteps(savedRun.getId(), policy.blueprint(), now);
            return new PolicyExecutionPlanningResult(PolicyExecutionRunStatus.PENDING, savedRun.getId(), null);
        } catch (DataIntegrityViolationException ex) {
            return handlePotentialDuplicate(request, idempotencyKey, ex);
        }
    }

    private PolicyExecutionPlanningResult persistBlockedPolicyRun(
            PolicyExecutionPlanRequest request,
            String idempotencyKey,
            AutomationPolicyService.ReadStatus status) {
        PolicyExecutionRunEntity run = new PolicyExecutionRunEntity();
        run.setSource(request.sourceSystem());
        run.setEventId(request.eventId());
        run.setWebhookEventId(request.webhookEventId());
        run.setSourceLeadId(request.sourceLeadId());
        run.setDomain(normalize(request.policyDomain()));
        run.setPolicyKey(normalize(request.policyKey()));
        run.setPolicyVersion(0L);
        run.setPolicyBlueprintSnapshot(Map.of());
        run.setStatus(PolicyExecutionRunStatus.BLOCKED_POLICY);
        run.setReasonCode(mapPolicyReadStatus(status));
        run.setIdempotencyKey(idempotencyKey);

        try {
            PolicyExecutionRunEntity saved = runRepository.saveAndFlush(run);
            return new PolicyExecutionPlanningResult(saved.getStatus(), saved.getId(), saved.getReasonCode());
        } catch (DataIntegrityViolationException ex) {
            return handlePotentialDuplicate(request, idempotencyKey, ex);
        }
    }

    private PolicyExecutionPlanningResult duplicateResult(PolicyExecutionRunEntity existingRun) {
        PolicyExecutionRunEntity run = Objects.requireNonNull(existingRun, "existingRun must not be null for duplicate results");
        return new PolicyExecutionPlanningResult(
                PolicyExecutionRunStatus.DUPLICATE_IGNORED,
                run.getId(),
                DUPLICATE_IDEMPOTENCY_KEY);
    }

    private PolicyExecutionPlanningResult handlePotentialDuplicate(
            PolicyExecutionPlanRequest request,
            String idempotencyKey,
            DataIntegrityViolationException ex) {
        if (!isIdempotencyConflict(ex)) {
            throw ex;
        }

        entityManager.clear();
        Optional<PolicyExecutionRunEntity> existingRun = runRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRun.isEmpty()) {
            throw new IllegalStateException("Idempotency conflict detected but no existing run found", ex);
        }

        log.info(
                "Duplicate policy execution planning ignored idempotencyKey={} eventId={} source={} policyDomain={} policyKey={}",
                idempotencyKey,
                request.eventId(),
                request.sourceSystem(),
                request.policyDomain(),
                request.policyKey());
        return duplicateResult(existingRun.get());
    }

    private void persistInitialSteps(Long runId, Map<String, Object> blueprint, OffsetDateTime now) {
        List<PolicyExecutionMaterializationContract.StepTemplate> templates = PolicyExecutionMaterializationContract.initialTemplates();
        for (PolicyExecutionMaterializationContract.StepTemplate template : templates) {
            PolicyExecutionStepEntity step = new PolicyExecutionStepEntity();
            step.setRunId(runId);
            step.setStepOrder(template.stepOrder());
            step.setStepType(template.stepType());
            step.setStatus(template.initialStatus());
            step.setDependsOnStepOrder(template.dependsOnStepOrder());
            step.setDueAt(resolveInitialDueAt(template, blueprint, now));
            stepRepository.save(step);
        }
        stepRepository.flush();
    }

    private OffsetDateTime resolveInitialDueAt(
            PolicyExecutionMaterializationContract.StepTemplate template,
            Map<String, Object> blueprint,
            OffsetDateTime now) {
        if (template.initialStatus() != PolicyExecutionStepStatus.PENDING) {
            return null;
        }
        int delayMinutes = resolveDelayMinutes(blueprint, template.stepType());
        return now.plusMinutes(Math.max(delayMinutes, 0));
    }

    private int resolveDelayMinutes(Map<String, Object> blueprint, PolicyStepType stepType) {
        if (blueprint == null || blueprint.isEmpty()) {
            return 0;
        }
        Object stepsObject = blueprint.get("steps");
        if (!(stepsObject instanceof List<?> steps)) {
            return 0;
        }
        for (Object stepObject : steps) {
            if (!(stepObject instanceof Map<?, ?> rawStep)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> step = (Map<String, Object>) rawStep;
            String type = normalize(String.valueOf(step.getOrDefault("type", "")));
            if (!stepType.name().equals(type)) {
                continue;
            }
            Object delayObject = step.get("delayMinutes");
            if (delayObject instanceof Number number) {
                return number.intValue();
            }
            return 0;
        }
        return 0;
    }

    private String buildIdempotencyKey(PolicyExecutionPlanRequest request) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(normalize(request.policyDomain()));
        joiner.add(normalize(request.policyKey()));
        joiner.add(request.sourceSystem() == null ? "UNKNOWN" : request.sourceSystem().name());
        joiner.add(request.normalizedDomain() == null ? "UNKNOWN" : request.normalizedDomain().name());
        joiner.add(request.normalizedAction() == null ? "UNKNOWN" : request.normalizedAction().name());
        joiner.add(normalize(request.sourceLeadId()));
        if (hasText(request.eventId())) {
            joiner.add("EVENT");
            joiner.add(request.eventId().trim());
        } else if (hasText(request.payloadHash())) {
            joiner.add("PAYLOAD");
            joiner.add(request.payloadHash().trim());
        } else {
            joiner.add("FALLBACK");
            joiner.add("NO_EVENT_OR_HASH");
        }
        return IDEMPOTENCY_KEY_PREFIX + sha256Hex(joiner.toString());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private boolean isIdempotencyConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String mapPolicyReadStatus(AutomationPolicyService.ReadStatus status) {
        if (status == null) {
            return POLICY_NOT_FOUND;
        }
        return switch (status) {
            case POLICY_INVALID -> POLICY_INVALID;
            case INVALID_INPUT -> POLICY_LOOKUP_INVALID_INPUT;
            case NOT_FOUND -> POLICY_NOT_FOUND;
            case SUCCESS -> POLICY_NOT_FOUND;
        };
    }

    private String normalize(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
