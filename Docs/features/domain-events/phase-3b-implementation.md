# Phase 3b — Implementation Log

Status: `DONE` — `fub_reassign` routes through the coordinator; A1–A7 codified and green. 644 tests pass.

Decision narrative per AGENTS.md convention.

## Deliverables shipped

| Layer | Surface |
|---|---|
| Step wrap | `FubReassignWorkflowStep` now calls `coordinator.applyScalarFieldUpdate(...)` with `Map.of("assignedUserId", LongNode.valueOf(targetUserId))` |
| Step test | `FubReassignWorkflowStepTest` (new — no test class existed for this step) — 17 tests |
| Race scenarios | `ReassignScenariosTest` (A1–A7) — 7 tests against real Postgres |
| Parity test patch | `WorkflowParityTest` seeds local person row in tests that exercise reassign |
| Harness lifecycle fix | `EngineWriteRaceHarness` switched to JVM-shared Postgres container |

## Why these decisions

### Retry stays inside the coordinator's `Supplier`

The wrap shape is:

```java
coordinator.applyScalarFieldUpdate(
    context.sourcePersonId(),
    Map.of("assignedUserId", LongNode.valueOf(targetUserId)),
    context.runId(),
    () -> fubCallHelper.executeWithRetry(
            () -> followUpBossClient.reassignPerson(personId, targetUserId)));
```

Retry is inside the `Supplier` passed to the coordinator. Two reasons:

1. If retry happened **outside** the coordinator call, the coordinator's REQUIRES_NEW tx (local update + tracker record) would commit once, then retry loops would call FUB without re-recording. State would be inconsistent across attempts.
2. The coordinator's job is "commit local intent atomically, then do the side effect once." Retries are part of the side effect, not part of the intent.

Verified by `retryHappensInsideCoordinatorSupplier_notOutsideIt`: mocks the helper to invoke the Supplier and asserts both `helper.executeWithRetry` AND `fub.reassignPerson` were called, proving the chain.

### `LongNode.valueOf` for the field value, then JSON round-trips to `IntNode`

`LongNode` is the simplest constructor for a `Long`-typed `targetUserId`. After the coordinator persists `personDetails`, Hibernate serializes to JSON and the next read deserializes — Jackson chooses `IntNode` for values fitting in `int`. This means production echoes (also from JSON parsing) compare type-correctly against the persisted state.

This works because **every comparison happens after a JSON round-trip on at least one side**. The same isn't true in tests that construct payloads with `createObjectNode().put(long)` — those produce `LongNode` without round-trip, which mismatches `IntNode` from DB load. See "Test bugs surfaced" below.

### Step constants promoted to `public`

`FubReassignWorkflowStep`'s result-code constants (`SOURCE_LEAD_ID_MISSING`, `FUB_REASSIGN_TRANSIENT`, etc.) were package-private. The new test class lives one package up (matching `FubAddTagWorkflowStepTest`'s convention). Two options: move tests into the steps package, or promote constants to `public`. Chose promotion — matches the existing `FubAddTagWorkflowStep` convention and avoids a one-off package split.

### `WorkflowParityTest` needed local person seeding

The parity test exercises `fub_reassign` end-to-end. Pre-Phase-3, the step never touched local state, so no `persons` row was needed. Post-3b, the coordinator's `findBy…ForUpdate` throws `IllegalStateException` if the row doesn't exist → step returns `REASSIGN_EXECUTION_ERROR` → parity assertion fails.

Fix: added a `seedPersonRow(sourcePersonId)` helper called by the two tests that actually execute reassign (`shouldReassignWhenNoCommunicationFound`, `shouldHandleReassignFailure`). The seed contains `assignedUserId=0, stage=Lead` — minimal valid state. The tests that test the SKIPPED path for reassign don't need the seed.

This change is honest about the new Phase 3 requirement: engine scalar writes target existing persons. Workflows triggered by webhooks always have an existing person (the webhook created it), so production behaviour is fine. Test fixtures need to match.

## A1–A7 — what each cell verifies

| Cell | Property verified |
|---|---|
| **A1** | Happy path: engine writes local, echo from FUB sees `local == payload`, emits 0 events. |
| **A2** | **Smoking-gun structural proof.** Sets `fakeFub.delayMs=500`. Engine starts on background thread; main thread fires the echo at t≈50ms and measures elapsed time. If the row lock were held across the FUB call, echo would block until t≈500ms+. Assertion: echo elapsed < FUB-delay/2. |
| **A3** | 3 burst echoes for one engine write → still 0 events; tracker still has the original record. |
| **A4** | Engine + concurrent external change. Both events emit, **both annotated `source=ENGINE`** under the subset-match rule. Documented in test as the annotation-over-detection bias. |
| **A5** | Permanent FUB failure → no revert → local stays at engine value → next echo carries FUB's actual state → emits 1 event annotated `ENGINE`. This is the misleading-echo-after-permanent-failure case (known-issue #26). |
| **A6** | Transient failure with concurrent external — same outcome as A4. Retry doesn't change the race shape. |
| **A7** | Two engine writes back-to-back on the same person. The pessimistic lock serializes them. Both FUB calls succeed. Final echoes annotated `ENGINE` regardless of PUT order — tracker has records for both writes. |

## Test bugs surfaced (worth recording)

### `PersonDiffComputer` is type-strict on `JsonNode.equals`

`Objects.equals(LongNode(200), IntNode(200))` → `false` (different Jackson classes). The first race-scenario run had 0-event tests emitting events because:

1. Engine wrote `LongNode(200)` via the coordinator.
2. Hibernate persisted as JSON; next load deserialized as `IntNode(200)`.
3. Test's `fireEcho` built payload with `createObjectNode().put("assignedUserId", 200L)` → `LongNode(200)`.
4. `buildSnapshot` of the test payload returned `LongNode` (no round-trip happened — the test built the JsonNode directly, not via JSON parsing).
5. Diff against the IntNode-loaded local → non-empty → event emitted.

The bug is in the test, not in production code. Real FUB webhooks arrive as JSON text, parsed by Jackson → `IntNode` for small values, matching the type of the round-tripped DB load. Fix in test: `fireEcho` constructs the payload by parsing a JSON string, matching production. The type fragility of the diff is a latent risk worth being aware of — documented as a follow-up consideration if future code paths construct `JsonNode` directly.

### Hikari pool exhaustion under many Spring contexts

The full suite has ~19+ `@SpringBootTest` classes. Several of them (mine and Phase 2's) use `@Container` with their own `PostgreSQLContainer`. When run sequentially, Docker resource accumulation caused some later containers (specifically `ReassignScenariosTest`'s) to fail to start — `HikariPool-19: Connection to localhost:60418 refused`.

Fix: replaced `@Container static` with a JVM-shared `static final PostgreSQLContainer` initialized in a static block, with shutdown hook. All race subclasses share one container for the JVM lifetime. The smoke test (`EngineWriteRaceHarnessSmokeTest`) and the scenarios (`ReassignScenariosTest`) now share one Postgres.

Side benefit: faster full-suite run because one Postgres startup, not two.

### `verify(coordinator).applyScalarFieldUpdate(...)` syntax cleanup

Initial draft of `happyPath_invokesCoordinatorWithExactFieldMapAndRunId_thenSucceeds` used a malformed Mockito verify construction (`ArgumentCaptor.forClass(...).capture() == null ? null : "20235"`) that happened to match leniently. Rewrote using two explicit captors and direct `assertEquals` against `personIdCap.getValue()`. The test was passing for the wrong reason; the rewrite makes it pass for the right reason.

## What's NOT in 3b

- No `change.source` consumer. `WorkflowTriggerRouter.route(NormalizedWebhookEvent)` still runs unchanged. Phase 4 wires the consumer.
- No engine-write event emission in production (gate stays `false`). The code path is exercised under `true` in `DefaultEngineWriteCoordinatorTest` only.
- No revert code path. Phase 3's design choice; A5 documents the known-issue #26 trade-off in test form.

## Repo decisions impact

`No` — feature-internal. The wrap pattern is now exemplified for one step; 3c (`fub_move_to_pond`) follows the same shape. If a generic `FubMutatingStep` SPI ever materializes (deferred per `phase-3-plan.md` §"Out of scope"), promote then.
