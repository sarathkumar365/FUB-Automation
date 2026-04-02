package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DefaultLeadIdentityResolver implements LeadIdentityResolver {

    @Override
    public Optional<String> resolveInternalLeadRef(WebhookSource sourceSystem, String sourceLeadId) {
        // Identity mapping boundary (RD-003) is intentionally explicit.
        // Until a concrete mapping adapter is introduced, unresolved identity is expected.
        // This intentionally causes planning to persist BLOCKED_IDENTITY runs for assignment triggers.
        // Do not replace with heuristic mapping here; wire a real adapter at this boundary instead.
        return Optional.empty();
    }
}
