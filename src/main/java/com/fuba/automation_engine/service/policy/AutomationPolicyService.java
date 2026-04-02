package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationPolicyService {

    private static final int DOMAIN_MAX_LENGTH = 64;
    private static final int POLICY_KEY_MAX_LENGTH = 128;
    private static final String ACTIVE_SCOPE_CONSTRAINT = "uk_automation_policies_active_per_scope";

    private final AutomationPolicyRepository repository;

    public AutomationPolicyService(AutomationPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LookupResult getActivePolicy(String domain, String policyKey) {
        String normalizedDomain = normalizeToken(domain, DOMAIN_MAX_LENGTH);
        String normalizedPolicyKey = normalizeToken(policyKey, POLICY_KEY_MAX_LENGTH);
        if (normalizedDomain == null || normalizedPolicyKey == null) {
            return new LookupResult(ReadStatus.INVALID_INPUT, null);
        }

        Optional<AutomationPolicyEntity> found = repository.findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc(
                normalizedDomain, normalizedPolicyKey, PolicyStatus.ACTIVE);
        if (found.isEmpty()) {
            return new LookupResult(ReadStatus.NOT_FOUND, null);
        }

        return new LookupResult(ReadStatus.SUCCESS, toView(found.get()));
    }

    @Transactional(readOnly = true)
    public ListResult listPolicies(String domain, String policyKey) {
        String normalizedDomain = normalizeToken(domain, DOMAIN_MAX_LENGTH);
        String normalizedPolicyKey = normalizeToken(policyKey, POLICY_KEY_MAX_LENGTH);
        if (normalizedDomain == null || normalizedPolicyKey == null) {
            return new ListResult(ReadStatus.INVALID_INPUT, List.of());
        }

        List<PolicyView> policies = repository.findByDomainAndPolicyKeyOrderByIdDesc(normalizedDomain, normalizedPolicyKey)
                .stream()
                .map(this::toView)
                .toList();

        return new ListResult(ReadStatus.SUCCESS, policies);
    }

    @Transactional
    public MutationResult createPolicy(CreatePolicyCommand command) {
        ValidationResult validation = validateCreate(command);
        if (!validation.valid()) {
            return new MutationResult(MutationStatus.INVALID_INPUT, null);
        }

        AutomationPolicyEntity entity = new AutomationPolicyEntity();
        entity.setDomain(validation.domain());
        entity.setPolicyKey(validation.policyKey());
        entity.setEnabled(command.enabled());
        entity.setDueAfterMinutes(command.dueAfterMinutes());
        entity.setStatus(PolicyStatus.INACTIVE);

        try {
            AutomationPolicyEntity saved = repository.saveAndFlush(entity);
            return new MutationResult(MutationStatus.SUCCESS, toView(saved));
        } catch (DataIntegrityViolationException ex) {
            return new MutationResult(mapIntegrityViolation(ex), null);
        }
    }

    @Transactional
    public MutationResult updatePolicy(long id, UpdatePolicyCommand command) {
        if (command == null || command.expectedVersion() == null || command.dueAfterMinutes() < 1) {
            return new MutationResult(MutationStatus.INVALID_INPUT, null);
        }

        Optional<AutomationPolicyEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return new MutationResult(MutationStatus.NOT_FOUND, null);
        }

        AutomationPolicyEntity entity = existing.get();
        if (!command.expectedVersion().equals(entity.getVersion())) {
            return new MutationResult(MutationStatus.STALE_VERSION, null);
        }

        entity.setEnabled(command.enabled());
        entity.setDueAfterMinutes(command.dueAfterMinutes());

        try {
            AutomationPolicyEntity saved = repository.saveAndFlush(entity);
            return new MutationResult(MutationStatus.SUCCESS, toView(saved));
        } catch (ObjectOptimisticLockingFailureException ex) {
            return new MutationResult(MutationStatus.STALE_VERSION, null);
        } catch (DataIntegrityViolationException ex) {
            return new MutationResult(mapIntegrityViolation(ex), null);
        }
    }

    @Transactional
    public MutationResult activatePolicy(long id, ActivatePolicyCommand command) {
        if (command == null || command.expectedVersion() == null) {
            return new MutationResult(MutationStatus.INVALID_INPUT, null);
        }

        Optional<AutomationPolicyEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return new MutationResult(MutationStatus.NOT_FOUND, null);
        }

        AutomationPolicyEntity target = existing.get();
        if (!command.expectedVersion().equals(target.getVersion())) {
            return new MutationResult(MutationStatus.STALE_VERSION, null);
        }

        try {
            repository.deactivateActivePoliciesInScopeExcludingId(
                    target.getDomain(),
                    target.getPolicyKey(),
                    target.getId(),
                    PolicyStatus.ACTIVE,
                    PolicyStatus.INACTIVE);

            target.setStatus(PolicyStatus.ACTIVE);
            AutomationPolicyEntity saved = repository.saveAndFlush(target);
            return new MutationResult(MutationStatus.SUCCESS, toView(saved));
        } catch (ObjectOptimisticLockingFailureException ex) {
            return new MutationResult(MutationStatus.STALE_VERSION, null);
        } catch (DataIntegrityViolationException ex) {
            return new MutationResult(mapIntegrityViolation(ex), null);
        }
    }

    private ValidationResult validateCreate(CreatePolicyCommand command) {
        if (command == null || command.dueAfterMinutes() < 1) {
            return ValidationResult.invalid();
        }

        String normalizedDomain = normalizeToken(command.domain(), DOMAIN_MAX_LENGTH);
        String normalizedPolicyKey = normalizeToken(command.policyKey(), POLICY_KEY_MAX_LENGTH);
        if (normalizedDomain == null || normalizedPolicyKey == null) {
            return ValidationResult.invalid();
        }

        return ValidationResult.valid(normalizedDomain, normalizedPolicyKey);
    }

    private String normalizeToken(String input, int maxLength) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        String upperCased = normalized.toUpperCase(Locale.ROOT);
        if (upperCased.length() > maxLength) {
            return null;
        }

        return upperCased;
    }

    private MutationStatus mapIntegrityViolation(DataIntegrityViolationException ex) {
        if (isActiveConflict(ex)) {
            return MutationStatus.ACTIVE_CONFLICT;
        }
        return MutationStatus.INVALID_INPUT;
    }

    private boolean isActiveConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(ACTIVE_SCOPE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private PolicyView toView(AutomationPolicyEntity entity) {
        return new PolicyView(
                entity.getId(),
                entity.getDomain(),
                entity.getPolicyKey(),
                entity.isEnabled(),
                entity.getDueAfterMinutes(),
                entity.getStatus(),
                entity.getVersion());
    }

    private record ValidationResult(boolean valid, String domain, String policyKey) {
        static ValidationResult invalid() {
            return new ValidationResult(false, null, null);
        }

        static ValidationResult valid(String domain, String policyKey) {
            return new ValidationResult(true, domain, policyKey);
        }
    }

    public enum MutationStatus {
        SUCCESS,
        INVALID_INPUT,
        NOT_FOUND,
        STALE_VERSION,
        ACTIVE_CONFLICT
    }

    public enum ReadStatus {
        SUCCESS,
        INVALID_INPUT,
        NOT_FOUND
    }

    public record PolicyView(
            Long id,
            String domain,
            String policyKey,
            boolean enabled,
            int dueAfterMinutes,
            PolicyStatus status,
            Long version) {
    }

    public record MutationResult(MutationStatus status, PolicyView policy) {
    }

    public record LookupResult(ReadStatus status, PolicyView policy) {
    }

    public record ListResult(ReadStatus status, List<PolicyView> policies) {
    }

    public record CreatePolicyCommand(String domain, String policyKey, boolean enabled, int dueAfterMinutes) {
    }

    public record UpdatePolicyCommand(boolean enabled, int dueAfterMinutes, Long expectedVersion) {
    }

    public record ActivatePolicyCommand(Long expectedVersion) {
    }
}
