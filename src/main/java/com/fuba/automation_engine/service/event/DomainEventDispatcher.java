package com.fuba.automation_engine.service.event;

/**
 * Fans a committed {@link DomainEvent} out to all registered
 * {@link DomainEventListener}s.
 *
 * <p>Called by {@link DomainEventEmitter}'s after-commit synchronization hook
 * — never inline inside the emitting transaction. By the time
 * {@code dispatch} runs, the {@code events} row is already durable, so
 * listener failures cannot lose the event.
 *
 * <p>The interface is the commitment; the implementation is swappable.
 * Phase 2 ships {@link InMemoryDomainEventDispatcher}. A future
 * Redis/queue-backed impl can take over without changing emission code or
 * listeners. See {@code Docs/features/domain-events/plan.md} §"The events
 * table" for the rationale (after-commit dispatch keeps emission and
 * consumption decoupled).
 */
public interface DomainEventDispatcher {

    void dispatch(DomainEvent event);
}
