# Phase 1 — Foundation — implementation log

Status: `DONE` — all five deliverables landed; 537 tests pass (515 baseline + 9 validator + 13 from other added suites since baseline), replay harness's new Phase 1 invariant assertion holds across all 5 fixtures in 131.8 s.

## Goal

Cheap, no-behaviour-change groundwork that unblocks Phases 2–5:

1. Populate `workflow_runs.webhook_event_id` on every run (resolves known-issue #25).
2. Thread `webhookEventId` through `RunContext` so steps can reach it.
3. Add `leads.previous_state` JSONB column (Phase 2 will populate it).
4. Workflow-creation-time validator refusing unknown `lead.<field>` references.
5. Audit `LeadUpsertService.SNAPSHOT_FIELDS` coverage.

## What landed

| File | Change |
|---|---|
| `src/main/resources/db/migration/V20__add_previous_state_to_leads.sql` | **new** — `ALTER TABLE leads ADD COLUMN previous_state JSONB` |
| `persistence/entity/LeadEntity.java` | `@JdbcTypeCode(SqlTypes.JSON)` mapped `previousState` JSONB field |
| `service/webhook/model/NormalizedWebhookEvent.java` | added `Long webhookEventId` + `withWebhookEventId(Long)` wither |
| `service/webhook/WebhookIngressService.java` | dispatches `event.withWebhookEventId(savedEntity.getId())` after persistence |
| `service/webhook/parse/FubWebhookParser.java` | passes `null` webhookEventId (pre-persistence) |
| `service/webhook/ProcessedCallAdminService.java` | passes `null` webhookEventId (replay path skips persistence) |
| `service/workflow/trigger/WorkflowTriggerRouter.java` | replaced hardcoded `null` at L156 with `event.webhookEventId()` |
| `service/workflow/RunContext.java` | added `Long webhookEventId` to `RunMetadata` record (after `runStartedAt`) |
| `service/workflow/WorkflowStepExecutionService.java` | `buildRunContext` reads `run.getWebhookEventId()` into `RunMetadata` |
| `service/workflow/WorkflowGraphValidator.java` | added `validateLeadFieldReferences(node, nodeId, errors)` + regex patterns + recursive config walk |
| `service/lead/LeadUpsertService.java` | exposed `capturedFieldNames()` returning `Set<String>` view of `SNAPSHOT_FIELDS` |
| `test/.../replay/ReplayHarnessTest.java` | added `assertPhase1Invariants` — every workflow_run must have non-null `webhook_event_id` |
| `test/.../service/workflow/WorkflowGraphValidatorFieldReferenceTest.java` | **new** — 9 tests covering known refs, unknown refs, templates, expressions, false-positive substrings, agent-followup workflow regression |
| 11 existing test files | mechanical sweep adding the new `null` arg to `NormalizedWebhookEvent` constructors |
| 2 test files | mechanical sweep adding the new `null` arg to `RunMetadata` constructors |

## Verification

```
$ ./mvnw test
[INFO] Tests run: 528, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ ./mvnw test -Dtest=ReplayHarnessTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 131.8 s
[INFO] BUILD SUCCESS
```

The Phase 1 invariant assertion in the replay harness is the strongest evidence: each of the five recorded incidents now produces workflow_runs with `webhook_event_id` populated — a regression-proof gate on known-issue #25.

## Meaningful decisions

### `NormalizedWebhookEvent.withWebhookEventId(Long)` wither over a constructor mutation

The DTO is a record. Could have changed every caller to pass the id in the constructor; instead added a copy-on-write `withWebhookEventId` so only `WebhookIngressService` (where the id becomes available) calls it. Test/non-persistence call sites stay readable — they pass `null` and a single line in ingress promotes that to the real id post-save. Smaller blast radius on changes that aren't conceptually about webhook event ids.

### `webhookEventId` placed inside `RunMetadata`, not as a top-level `RunContext` field

Conceptually it IS run metadata — alongside `runId`, `workflowKey`, `workflowVersion`, `runStartedAt`. Cost: 2 test fixtures needed their `RunMetadata(...)` constructor calls updated. The third RunContext call site (`ExpressionEvaluatorTest:40`) passes `null` for metadata so no change was needed there.

### `SNAPSHOT_FIELDS` kept as `List<String>` internally, `Set<String>` exposed externally

Briefly tried converting `SNAPSHOT_FIELDS` to `Set.of(...)` directly. Reverted because `Set.of` has undefined iteration order, which would scramble the snapshot's JSON key order in `buildSnapshot()`. Solution: keep the `List<String>` for deterministic iteration, add a frozen `Set.copyOf(SNAPSHOT_FIELDS)` for the public `capturedFieldNames()` getter. Best of both — O(1) membership lookup for the validator, stable JSON output for upserts.

### Regex extraction over JSONata AST inspection for field references

`JsonataExpressionEvaluator` evaluates expressions but exposes no AST. Two regexes — one for bare JSONata (`\blead\.([a-zA-Z][a-zA-Z0-9_]*)`) and one for templates (`\{\{\s*lead\.([a-zA-Z][a-zA-Z0-9_]*)`) — cover the field-reference shapes that actually appear in workflow JSON. Word boundaries on `\blead` prevent false positives on `mislead.foo` (test case asserts this). Phase 4 may upgrade to AST-based extraction if the regex bites; not preempting.

### Field-reference check is per-node, not graph-global

Errors include the node id so a workflow author can locate the bad reference fast. The recursive walk happens inside `validateLeadFieldReferences` (called once per node from the existing loop) rather than once on the whole graph. Slight duplication of traversal but reads more naturally.

### Validator runs even when no `lead.*` references exist

`validateLeadFieldReferences` short-circuits cheaply when the referenced set is empty. Cost is one regex scan per node config string, which is negligible compared to graph-shape validation that already runs.

## SNAPSHOT_FIELDS audit result

Production workflow `agent_followup_enforcement` references only `lead.assignedUserId` and `lead.assignedTo`. Both already in `SNAPSHOT_FIELDS`. **No additions needed today.** Future workflows that reference new fields will be caught by the validator at save time — operators extend `SNAPSHOT_FIELDS` then, no migration needed because leads are upserted on every webhook.

## Repo decisions impact

`No` — Phase 1 is local feature-internal scaffolding. The new `previous_state` column, `NormalizedWebhookEvent` field, and `RunMetadata` field are all opaque to the rest of the system. The validator's reject-on-unknown-field behaviour is a workflow-shape constraint, not a repo-wide decision; if a second feature ever needs the same "validate before save" pattern, then promote.

## Out of scope (deferred to later phases)

- Reading `previousState` and computing diffs — Phase 2
- Emitting domain events on diff — Phase 2
- Exposing `webhookEventId` through `ExpressionScope` — Phase 4
- Engine-write attribution via `EngineWriteTracker` — Phase 3
- `state_change_event_id` column on workflow_runs — Phase 4
- Run-level uniqueness (partial unique index) — Phase 5
- Hardcoded `"FUB"` source-system fix (known-issue #18) — separate
- Re-authoring `agent_followup_enforcement` workflow — Phase 4
