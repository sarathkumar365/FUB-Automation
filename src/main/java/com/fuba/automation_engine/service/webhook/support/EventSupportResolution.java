package com.fuba.automation_engine.service.webhook.support;

import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import java.util.Objects;

public record EventSupportResolution(
        EventSupportState supportState,
        NormalizedDomain normalizedDomain,
        NormalizedAction normalizedAction,
        String notes) {

    public EventSupportResolution {
        Objects.requireNonNull(supportState, "supportState must not be null");
        Objects.requireNonNull(normalizedDomain, "normalizedDomain must not be null");
        Objects.requireNonNull(normalizedAction, "normalizedAction must not be null");
    }
}
