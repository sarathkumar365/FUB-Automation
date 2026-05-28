package com.fuba.automation_engine.service.event;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-process fan-out of {@link DomainEvent}s to all
 * {@link DomainEventListener} beans Spring discovers.
 *
 * <p>Spring's constructor injection of {@code List<DomainEventListener>}
 * collects every {@code @Component} that implements the interface — this is
 * the registration mechanism committed in
 * {@code Docs/features/domain-events/phase-2-plan.md}. Phase 2 ships with the
 * list empty; Phase 4 wires the first listener by declaring a component, with
 * no code change needed here.
 *
 * <p>Per-listener error isolation: an exception from one listener is caught
 * and logged at ERROR; remaining listeners still receive the event. The event
 * row is already durably committed by the time dispatch runs (after-commit
 * hook in {@link DomainEventEmitter}), so a listener failure cannot lose the
 * event — it can only fail to act on it. The choice keeps fan-out semantics
 * honest: one misbehaving listener does not starve siblings. Traceability of
 * per-listener failures can be layered on later (e.g. dispatched-flag table)
 * without changing this contract.
 */
@Component
public class InMemoryDomainEventDispatcher implements DomainEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDomainEventDispatcher.class);

    private final List<DomainEventListener> listeners;

    public InMemoryDomainEventDispatcher(List<DomainEventListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void dispatch(DomainEvent event) {
        if (listeners.isEmpty()) {
            return;
        }
        for (DomainEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                log.error(
                        "Domain event listener failed eventKind={} entityType={} entityId={} listener={}",
                        event.eventKind(),
                        event.entityType(),
                        event.entityId(),
                        listener.getClass().getSimpleName(),
                        ex);
            }
        }
    }
}
