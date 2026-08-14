package com.flux.service.webhook.support;

import com.flux.service.webhook.model.EventSupportState;
import com.flux.service.webhook.model.NormalizedAction;
import com.flux.service.webhook.model.NormalizedDomain;
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
