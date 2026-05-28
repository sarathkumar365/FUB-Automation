package com.fuba.automation_engine.service.event;

/**
 * Receives a {@link DomainEvent} after the emitting transaction commits.
 *
 * <p>Phase 2 registers no listeners — the dispatcher's listener list is empty
 * and {@link DomainEventDispatcher#dispatch(DomainEvent)} is a no-op. Phase 4
 * adds the first listener (the workflow trigger router) by declaring a
 * {@code @Component} that implements this interface; Spring's constructor
 * injection of {@code List<DomainEventListener>} picks it up automatically,
 * so no registration code needs to change here.
 *
 * <p>Implementations must be safe to call from the after-commit thread of the
 * emitting transaction. A listener failure must not lose the event — by the
 * time {@code onEvent} runs, the {@code events} row is already durably
 * committed.
 */
public interface DomainEventListener {

    void onEvent(DomainEvent event);
}
