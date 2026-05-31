# Phase 3a — Implementation Log

Status: `DONE` — scaffold landed; no production caller of the coordinator yet, no behaviour change. 620 tests green (589 baseline + 31 new).

Decision narrative for Phase 3a per the AGENTS.md convention: answers *"why was it built this way?"* — git is the source of truth for what changed.

See [`phase-3-plan.md`](./phase-3-plan.md) for the locked plan, [`phase-3-race-matrix.md`](./phase-3-race-matrix.md) for the analysis that drove the design.

## Deliverables shipped

| Layer | Surface |
|---|---|
| Tracker interface | `EngineWriteTracker`, `EngineWriteRecord` |
| Tracker impl | `InMemoryEngineWriteTracker` (`ConcurrentHashMap` + `@Scheduled` eviction) |
| Coordinator interface | `EngineWriteCoordinator` (3 op modes) |
| Coordinator impl | `DefaultEngineWriteCoordinator` (REQUIRES_NEW inner tx) |
| Emitter hook | `DomainEventEmitter.maybeAnnotateEngineSource(...)` |
| Config | `engine.write.tracker.ttl-seconds`, `eviction-interval-ms`, `emit-events` |
| Test infra | `FakeFollowUpBossClient`, `EngineWriteRaceHarness` (skeleton) |
| Tests | tracker (12), coordinator (12), emitter annotation (6), harness smoke (1) |

## Why these decisions

### No `markFailed` on the tracker

The plan dropped revert entirely. `RetryPolicy.DEFAULT_FUB` handles transient FUB failures; permanent failures accept drift until the next webhook re-syncs. There's no failure state for a tracker to record. The interface stayed honest by not exposing a method nobody calls.

### Subset match — annotation-over-detection bias

`findMatching` returns a hit when `record.changedFields ⊆ diffFields`. The looser direction (annotate when the diff is at least the engine's intent, even if more fields changed) deliberately annotates concurrent-external scenarios as `ENGINE`. Strict equality would miss the A4 case in the matrix.

The trade-off is documented as a deliberate platform choice. Phase 4 workflows filtering `change.source != "ENGINE"` will suppress some events triggered by external concurrent changes. The alternative — letting phantom events flow through — was the worse failure mode.

Pinned by `findMatchingSubsetHits_engineFieldsAreSubsetOfDiff` and `findMatchingSupersetMisses_engineWroteMoreThanDiffShows` in the tracker test.

### Engine event emission is gated, not yet emitting

`engine.write.emit-events` defaults to `false`. The coordinator prepares the would-be `person.state_changed` payload and logs it at INFO with the marker `[engine-write-emit:LOG_ONLY]`. The `emit(...)` code path is exercised under `true` via a dedicated unit test (`scalarEmitsAnnotatedEvent_whenEmitEventsTrue`) so Phase 4 can flip the flag without code surprises.

Decision context: the user asked for the code path to be ready but not active. Keeps the implementation honest (no dead code, no theoretical paths) while preserving today's event-stream behaviour.

### `REQUIRES_NEW` is a real, tested requirement

[Smoking gun](./phase-3-race-matrix.md#smoking-gun): `WorkflowStepExecutionService.executeClaimedStep` is `@Transactional`. If the coordinator's inner write joined that outer tx, the row lock would be held across the FUB HTTP call and across the entire step's lifecycle. Under burst load this would pin the 2–4 thread webhook async pool.

Mitigation: `TransactionTemplate(REQUIRES_NEW)` for all inner writes. The unit test `scalarRequiresNew_outerRollbackDoesNotUndoInnerWrite` wraps the coordinator call in an outer `TransactionTemplate`, marks rollback-only, and verifies the inner write persists. The defence is structural, not aspirational.

### Annotation lives in `payload.source`, not on the `DomainEvent` record

Choosing a new field on `DomainEvent` would have churned every listener signature and forced Phase 2's existing dispatcher tests to be updated. Storing in the JSON payload is what Phase 4 consumers will read anyway (workflow filters access `change.source`). The emitter mutates the payload in place when it's an `ObjectNode`, or wraps via `ObjectMapper.valueToTree` when not.

### Tracker key shape

`(entityType, entityId)`. `runId` is recorded on each `EngineWriteRecord` for the Phase 4 audit trail but is **not** part of the match key. Rationale: two engine writes for the same person from different workflow runs (A7) should both annotate echoes. Keying by `runId` would partition tracker records and cause spurious misses on the second write's echo.

### `ConcurrentHashMap.compute` for thread-safe per-key list mutation

The naive shape (read list, mutate, put back) has a TOCTOU race. `compute(...)` holds the per-bucket lock for the duration of the lambda, making `record(...)`, `findMatching(...)`, and `evictExpired(...)` atomic per key. The internal list inside each bucket is mutated exclusively under that lock, so the un-synchronized `ArrayList` is safe by construction.

Verified by `concurrentRecordAndFindIsThreadSafe` — 20 threads × 50 ops with no record loss.

### Test infrastructure for 3b/3c/3d/3e to extend

`EngineWriteRaceHarness` and `FakeFollowUpBossClient` ship in 3a as skeletons. The smoke test (`EngineWriteRaceHarnessSmokeTest`) exists solely to prove the harness wires correctly — concrete A/B/C/D scenarios land in their respective sub-phases. This is the same playbook Phase 2 followed (Phase 2a built the scaffold; Phase 2c put it to use).

## Defaults locked in code

| Default | Value | Why |
|---|---|---|
| Tracker impl | `InMemoryEngineWriteTracker` | Phase 3 has no consumers; crash-window phantom-event class is dev-acceptable. Redis impl is a Phase 4 prerequisite. |
| TTL | 30s | Covers FUB echo arrival window (300–800ms) with safe headroom. |
| Eviction interval | 10s | Bounded loop cost; no observable lag for echo annotation. |
| `emit-events` | `false` | Phase 3 default; Phase 4 flips. |
| `source` annotation location | `event.payload.source` | Avoids churning listener signatures. |
| Match semantics | subset (`record ⊆ diff` → hit) | Annotation-over-detection bias; alternative misses A4. |

## Repo decisions impact

`No` — feature-internal. No new project-wide convention. The `*Coordinator` naming for op-mode-dispatching services and the gated-emission pattern (`[engine-write-emit:LOG_ONLY]` log marker) are local conventions; if a future feature replicates them, promote to `Docs/repo-decisions/`.
