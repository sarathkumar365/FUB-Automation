package com.fuba.automation_engine.service.webhook.model;

import com.fuba.automation_engine.service.webhook.support.EventSupportResolution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventSupportStateContractTest {

    @Test
    void shouldExposeExpectedStateValues() {
        assertEquals(EventSupportState.SUPPORTED, EventSupportState.valueOf("SUPPORTED"));
        assertEquals(EventSupportState.STAGED, EventSupportState.valueOf("STAGED"));
        assertEquals(EventSupportState.IGNORED, EventSupportState.valueOf("IGNORED"));
    }

    @Test
    void shouldRejectNullCoreFieldsInResolutionContract() {
        assertThrows(NullPointerException.class, () -> new EventSupportResolution(
                null,
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                null));
        assertThrows(NullPointerException.class, () -> new EventSupportResolution(
                EventSupportState.SUPPORTED,
                null,
                NormalizedAction.CREATED,
                null));
        assertThrows(NullPointerException.class, () -> new EventSupportResolution(
                EventSupportState.SUPPORTED,
                NormalizedDomain.CALL,
                null,
                null));
    }
}
