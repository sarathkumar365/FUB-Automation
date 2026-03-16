package com.fuba.automation_engine.rules;

import com.fuba.automation_engine.service.model.CallDetails;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CallPreValidationService {

    public Optional<PreValidationResult> validate(CallDetails callDetails) {
        if (callDetails.userId() == null || callDetails.userId() <= 0) {
            return Optional.of(new PreValidationResult(CallDecisionAction.SKIP, CallDecisionEngine.REASON_MISSING_ASSIGNEE));
        }

        if (callDetails.duration() != null && callDetails.duration() < 0) {
            return Optional.of(new PreValidationResult(CallDecisionAction.FAIL, CallDecisionEngine.REASON_INVALID_DURATION));
        }

        return Optional.empty();
    }

    public ValidatedCallContext normalize(CallDetails callDetails) {
        return new ValidatedCallContext(
                callDetails.id(),
                normalizePersonId(callDetails.personId()),
                callDetails.duration(),
                callDetails.userId(),
                normalizeOutcome(callDetails.outcome()));
    }

    private Long normalizePersonId(Long personId) {
        if (personId == null || personId <= 0) {
            return null;
        }
        return personId;
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null) {
            return null;
        }
        String trimmed = outcome.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase();
    }
}
