# Reference: Enums, Known Gaps, and Verification

> ⚠️ **Staleness banner (2026-06-03).** This deep-dive set predates the **Lead→Person rename (V21)** and the **domain-events feature** (typed domain events / Rail 2, the `events` table V22, `workflow_runs.domain_event_id` V23, the engine-echo gate, and run-supersede). Where it describes webhooks triggering workflows directly, a `leads` table / `sourceLeadId`, a 5-method FUB client, or the legacy policy engine as live, treat it as historical. Current sources: [`../features/domain-events/overview.md`](../features/domain-events/overview.md), [`../features/domain-events/current-wiring.md`](../features/domain-events/current-wiring.md), and the latest Flyway migrations (V23). A full content refresh of this set is pending.

## Enum Reference

### Webhook enums

| Enum | Values |
|------|--------|
| `WebhookSource` | `FUB` (path: `"fub"`), `INTERNAL` (path: `"internal"`) |
| `WebhookEventStatus` | `RECEIVED` |
| `EventSupportState` | `SUPPORTED`, `STAGED`, `IGNORED` |
| `NormalizedDomain` | `LEAD`, `CALL`, `UNKNOWN` (renamed from `ASSIGNMENT` → `LEAD` in V18) |
| `NormalizedAction` | `CREATED`, `UPDATED`, `UNKNOWN` (`ASSIGNED` was dropped — phantom value, no parser produced it) |

### Processed call enums

| Enum | Values |
|------|--------|
| `ProcessedCallStatus` | `RECEIVED`, `PROCESSING`, `SKIPPED`, `TASK_CREATED`, `FAILED` |

> **Removed:** the `PolicyStatus` / `PolicyExecutionRunStatus` / `PolicyExecutionStepStatus` / `PolicyStepType` / `PolicyStepResultCode` / `PolicyTerminalOutcome` enums were dropped with the policy subsystem (V12). Their replacement in the workflow engine is `WorkflowStatus`, `WorkflowRunStatus`, `WorkflowRunStepStatus`, etc. — see `persistence/entity/`.

---

## Known Gaps / Deferred Items

| # | Issue | Impact | Location |
|---|-------|--------|----------|
| 1 | Call processing non-atomic claim | Duplicate concurrent deliveries can both pass terminal guard | `WebhookEventProcessorService.processCall` |
| 2 | SSE `Map.of` null safety | `WebhookSseHub.publish` can NPE if `eventId` is null | `WebhookSseHub` |
| 3 | Replay doesn't reset `retryCount` | Replayed calls show inflated retry counts from previous attempt | `ProcessedCallAdminService.replay` |
| 4 | Duplicate detection too broad on save | `DataIntegrityViolationException` catch masks non-duplicate integrity failures | `WebhookIngressService.ingest` |
| 5 | Parser `sourceLeadId` always null | Lead ID not extracted from `peopleCreated`/`peopleUpdated` payloads — derived later in processor from `resourceIds` instead. | `FubWebhookParser` |
| 6 | Decision engine outcome-first ordering | Stale outcome labels can trigger tasks on connected calls | `CallDecisionEngine.decide` |

For the live, maintained issue tracker see [Docs/engineering-reference/known-issues.md](../engineering-reference/known-issues.md).

---

> **Verification Log removed:** the earlier 10-pass verification was anchored to commit `83821d1` and validated the policy subsystem (now dropped) and the V1–V8 schema (extended through V20+ since). Re-establishing verification at the current codebase state is tracked separately.
