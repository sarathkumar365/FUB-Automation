package com.fuba.automation_engine.service.policy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PolicyBlueprintValidator {

    // NOTE: This validator is intentionally narrow in Phase 3 and only accepts the v1
    // assignment follow-up template. This must evolve to support multiple policy templates
    // via a template registry + per-template validator strategy before enabling broader
    // workflow/policy authoring from UI.
    // Suggested target shape:
    // - interface PolicyTemplateValidator { supports(templateKey), validate(blueprint) }
    // - one implementation per template (e.g. AssignmentFollowupSlaV1TemplateValidator)
    // - central PolicyBlueprintValidationService that resolves validator by templateKey
    //   and returns deterministic error codes (including UNKNOWN_TEMPLATE).
    public static final String TEMPLATE_KEY_ASSIGNMENT_V1 = "ASSIGNMENT_FOLLOWUP_SLA_V1";

    private static final String FIELD_TEMPLATE_KEY = "templateKey";
    private static final String FIELD_STEPS = "steps";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DELAY_MINUTES = "delayMinutes";
    private static final String FIELD_DEPENDS_ON = "dependsOn";
    private static final String FIELD_ACTION_CONFIG = "actionConfig";
    private static final String FIELD_ACTION_TYPE = "actionType";
    private static final String FIELD_TARGET_USER_ID = "targetUserId";
    private static final String FIELD_TARGET_POND_ID = "targetPondId";

    private static final List<PolicyStepType> EXPECTED_STEP_ORDER = List.of(
            PolicyStepType.WAIT_AND_CHECK_CLAIM,
            PolicyStepType.WAIT_AND_CHECK_COMMUNICATION,
            PolicyStepType.ON_FAILURE_EXECUTE_ACTION);

    private static final Set<String> ALLOWED_ACTION_TYPES = Set.of("REASSIGN", "MOVE_TO_POND");

    private PolicyBlueprintValidator() {
    }

    public static ValidationResult validate(Map<String, Object> blueprint) {
        if (blueprint == null || blueprint.isEmpty()) {
            return ValidationResult.failure(ValidationCode.MISSING_BLUEPRINT);
        }

        String templateKey = normalizeText(blueprint.get(FIELD_TEMPLATE_KEY));
        if (!TEMPLATE_KEY_ASSIGNMENT_V1.equals(templateKey)) {
            return ValidationResult.failure(ValidationCode.INVALID_TEMPLATE_KEY);
        }

        Object stepsObject = blueprint.get(FIELD_STEPS);
        if (!(stepsObject instanceof List<?> steps) || steps.size() != EXPECTED_STEP_ORDER.size()) {
            return ValidationResult.failure(ValidationCode.INVALID_STEP_COUNT);
        }

        for (int i = 0; i < EXPECTED_STEP_ORDER.size(); i++) {
            Object stepObject = steps.get(i);
            if (!(stepObject instanceof Map<?, ?> rawStep)) {
                return ValidationResult.failure(ValidationCode.INVALID_JSON_SHAPE);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> step = (Map<String, Object>) rawStep;
            PolicyStepType expected = EXPECTED_STEP_ORDER.get(i);
            String stepTypeText = normalizeText(step.get(FIELD_TYPE));
            if (!expected.name().equals(stepTypeText)) {
                return ValidationResult.failure(ValidationCode.INVALID_STEP_ORDER);
            }

            if ((expected == PolicyStepType.WAIT_AND_CHECK_CLAIM
                    || expected == PolicyStepType.WAIT_AND_CHECK_COMMUNICATION)
                    && extractPositiveInt(step.get(FIELD_DELAY_MINUTES)) < 1) {
                return ValidationResult.failure(ValidationCode.INVALID_DELAY);
            }

            if (expected == PolicyStepType.WAIT_AND_CHECK_COMMUNICATION
                    && !PolicyStepType.WAIT_AND_CHECK_CLAIM.name().equals(normalizeText(step.get(FIELD_DEPENDS_ON)))) {
                return ValidationResult.failure(ValidationCode.INVALID_DEPENDENCY);
            }

            if (expected == PolicyStepType.ON_FAILURE_EXECUTE_ACTION
                    && !PolicyStepType.WAIT_AND_CHECK_COMMUNICATION.name().equals(normalizeText(step.get(FIELD_DEPENDS_ON)))) {
                return ValidationResult.failure(ValidationCode.INVALID_DEPENDENCY);
            }
        }

        Object actionConfigObject = blueprint.get(FIELD_ACTION_CONFIG);
        if (!(actionConfigObject instanceof Map<?, ?> rawActionConfig)) {
            return ValidationResult.failure(ValidationCode.INVALID_JSON_SHAPE);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> actionConfig = (Map<String, Object>) rawActionConfig;

        String actionType = normalizeText(actionConfig.get(FIELD_ACTION_TYPE));
        if (!ALLOWED_ACTION_TYPES.contains(actionType)) {
            return ValidationResult.failure(ValidationCode.INVALID_ACTION_TYPE);
        }
        if ("REASSIGN".equals(actionType)) {
            ValidationCode targetValidation = validatePositiveTarget(actionConfig, FIELD_TARGET_USER_ID);
            if (targetValidation != null) {
                return ValidationResult.failure(targetValidation);
            }
        }
        if ("MOVE_TO_POND".equals(actionType)) {
            ValidationCode targetValidation = validatePositiveTarget(actionConfig, FIELD_TARGET_POND_ID);
            if (targetValidation != null) {
                return ValidationResult.failure(targetValidation);
            }
        }

        return ValidationResult.success();
    }

    private static int extractPositiveInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static Long extractPositiveLong(Object value) {
        // TODO(known-issues#7): Reject non-integer numeric targets (e.g., 12.9) instead of truncating via longValue().
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static ValidationCode validatePositiveTarget(Map<String, Object> actionConfig, String fieldName) {
        if (!actionConfig.containsKey(fieldName)) {
            return ValidationCode.MISSING_ACTION_TARGET;
        }
        return extractPositiveLong(actionConfig.get(fieldName)) == null
                ? ValidationCode.INVALID_ACTION_TARGET
                : null;
    }

    private static String normalizeText(Object value) {
        if (!(value instanceof String text)) {
            return "";
        }
        return text.trim().toUpperCase(Locale.ROOT);
    }

    public enum ValidationCode {
        VALID,
        MISSING_BLUEPRINT,
        INVALID_JSON_SHAPE,
        INVALID_TEMPLATE_KEY,
        INVALID_STEP_COUNT,
        INVALID_STEP_ORDER,
        INVALID_DELAY,
        INVALID_DEPENDENCY,
        INVALID_ACTION_TYPE,
        MISSING_ACTION_TARGET,
        INVALID_ACTION_TARGET
    }

    public record ValidationResult(boolean valid, ValidationCode code) {
        static ValidationResult success() {
            return new ValidationResult(true, ValidationCode.VALID);
        }

        static ValidationResult failure(ValidationCode code) {
            return new ValidationResult(false, code);
        }
    }
}
