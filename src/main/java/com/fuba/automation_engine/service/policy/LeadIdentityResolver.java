package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Optional;

public interface LeadIdentityResolver {

    Optional<String> resolveInternalLeadRef(WebhookSource sourceSystem, String sourceLeadId);
}
