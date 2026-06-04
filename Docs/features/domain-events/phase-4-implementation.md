# Phase 4 — Implementation log (the rail switch)

> **Status:** Done (2026-06-03). Shipped as sub-phases 4a → 4b → 4c → 4d.1 → 4d.2. This is the decision narrative; the commit-level breakdown is in [`phase-4-plan.md`](./phase-4-plan.md). Phase 4 is the headline phase — workflows stop reacting to raw webhooks and subscribe to typed domain events; the bad-run-rate win lands here.

## What Phase 4 delivered

Workflows now consume **typed domain events** ("Rail 2") instead of raw webhooks ("Rail 1", deleted). The one production workflow (`agent_followup_enforcement`) was re-authored to `{ "on": "person.state_changed", "filter": "person.kind = 'LEAD' and change.assignedUserId.changed" }`. Replaying the recorded incident bursts end-to-end, the bad-run rate drops from ~50% to <5% — each burst now collapses to one run.

## The sub-phases

- **4a — substrate.** Added `workflow_runs.domain_event_id` (Flyway V23) and plumbed it through `WorkflowPlanRequest`/`WorkflowExecutionManager`. The originally-planned `suppressed_by_run_id` was dropped (Phase 5 became supersede, not a run-to-run link).
- **4b — trigger type + scope.** `DomainEvent` gained an `id`; `DomainEventScopeBuilder` builds the trigger-time JSONata scope (`event.*`, `change.*`, `current.*`, `event.origin`, `person.*`), reused at step time so trigger- and step-time scopes can't drift. `DomainEventTriggerType` matches `{on, filter}` against an event and extracts entities.
- **4c — validator.** `DomainEventTriggerValidator` validates `{on, filter, reactToEngineEvents}` and exposes `knownEventKinds()` for the admin `/trigger-types` surface.
- **4d.1 — engine-echo gate (RD-006).** `EngineEchoGate`: `shouldExclude = engineCaused AND NOT (globalCapability AND reactToEngineEvents)`, both default OFF. Flipped `engine.write.emit-events` on; added the `engine.events.workflow-consumption.enabled` platform-capability flag.
- **4d.2 — the switch.** `WorkflowTriggerRouter` became a `DomainEventListener` (`onEvent → route(DomainEvent)`); `WebhookEventProcessorService` no longer routes (emission + after-commit dispatch is the only path to a run). Deleted Rail 1 entirely: `FubWebhookTriggerType`, `WorkflowTriggerType`, `WorkflowTriggerRegistry`, `TriggerMatchContext` (+ tests). Admin/service layers now validate and surface the `{on, filter}` shape.

## Key decisions & surprises

- **Listener wiring caused a startup cycle.** Registering `WorkflowTriggerRouter` as a `DomainEventListener` created `DomainEventEmitter → Dispatcher → (listeners) Router → WorkflowExecutionManager → step types → EngineWriteCoordinator → DomainEventEmitter`. Broken with `@Lazy` on `InMemoryDomainEventDispatcher`'s `List<DomainEventListener>` (listeners aren't needed until dispatch, always post-startup).
- **`event.origin` collision.** `source` is both a diffable person field and the engine-write annotation key. Exposed provenance as `event.origin` (always `ENGINE`/`EXTERNAL`, never absent — an absent value would make `event.origin != 'ENGINE'` evaluate to JSONata *undefined* and silently drop real events).
- **known-issue #31 — the after-commit transaction gap (caught by the replay harness).** Under Rail 1 the router ran inside the live webhook transaction; under Rail 2 it runs in the `afterCommit` hook, where the committing transaction is still bound. `plan()` was `@Transactional` (REQUIRED) → it joined the completing transaction → `saveAndFlush` threw "No active transaction" → **zero runs would have been created in production**. Fixed with `@Transactional(REQUIRES_NEW)` on `plan()`. The replay harness replaying real bursts surfaced this (0 runs where 1 was expected) — exactly its job.

## Validation

The replay harness (`ReplayHarnessTest`) replays the five recorded incident fixtures end-to-end; each burst collapses to exactly one run. Full suite green at the cutover. See [`phases.md`](./phases.md) Phase 4 and the bad-run-rate evidence in [`plan.md`](./plan.md) §validation.

## Repo decisions impact

`Yes` — enforces **RD-006** (engine-echo exclusion, safe-by-default) via `EngineEchoGate` + the `reactToEngineEvents`/`workflow-consumption.enabled` two-level gate. No new RD created.
