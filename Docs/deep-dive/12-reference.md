# Reference: Enums, Known Gaps, and Verification

## Enum Reference

### Webhook enums

| Enum | Values |
|------|--------|
| `WebhookSource` | `FUB` (path: `"fub"`), `INTERNAL` (path: `"internal"`) |
| `WebhookEventStatus` | `RECEIVED` |
| `EventSupportState` | `SUPPORTED`, `STAGED`, `IGNORED` |
| `NormalizedDomain` | `CALL`, `ASSIGNMENT`, `UNKNOWN` |
| `NormalizedAction` | `CREATED`, `UPDATED`, `ASSIGNED`, `UNKNOWN` |

### Processed call enums

| Enum | Values |
|------|--------|
| `ProcessedCallStatus` | `RECEIVED`, `PROCESSING`, `SKIPPED`, `TASK_CREATED`, `FAILED` |

### Policy enums

| Enum | Values |
|------|--------|
| `PolicyStatus` | `ACTIVE`, `INACTIVE` |
| `PolicyExecutionRunStatus` | `PENDING`, `BLOCKED_POLICY`, `DUPLICATE_IGNORED`, `COMPLETED`, `FAILED` |
| `PolicyExecutionStepStatus` | `PENDING`, `WAITING_DEPENDENCY`, `PROCESSING`, `COMPLETED`, `FAILED`, `SKIPPED` |
| `PolicyStepType` | `WAIT_AND_CHECK_CLAIM`, `WAIT_AND_CHECK_COMMUNICATION`, `ON_FAILURE_EXECUTE_ACTION` |
| `PolicyStepResultCode` | `CLAIMED`, `NOT_CLAIMED`, `COMM_FOUND`, `COMM_NOT_FOUND`, `ACTION_SUCCESS`, `ACTION_FAILED` |
| `PolicyTerminalOutcome` | `NON_ESCALATED_CLOSED`, `COMPLIANT_CLOSED`, `ACTION_COMPLETED`, `ACTION_FAILED` |

---

## Known Gaps / Deferred Items

| # | Issue | Impact | Location |
|---|-------|--------|----------|
| 1 | Action mutation endpoints still log-only | Execution path is complete and returns `ACTION_SUCCESS`, but external provider mutation is deferred | `OnCommunicationMissActionStepExecutor`, `FubFollowUpBossClient` |
| 2 | Stale `PROCESSING` watchdog/reaper missing | Resolved in Phase 6 | `PolicyExecutionDueWorker` |
| 3 | Call processing non-atomic claim | Duplicate concurrent deliveries can both pass terminal guard | `WebhookEventProcessorService.processCall` |
| 4 | SSE `Map.of` null safety | `WebhookSseHub.publish` can NPE if `eventId` is null | `WebhookSseHub` |
| 5 | Replay doesn't reset `retryCount` | Replayed calls show inflated retry counts from previous attempt | `ProcessedCallAdminService.replay` |
| 6 | Duplicate detection too broad on save | `DataIntegrityViolationException` catch masks non-duplicate integrity failures | `WebhookIngressService.ingest` |
| 7 | Parser `sourceLeadId` always null | Lead ID not extracted from `peopleCreated`/`peopleUpdated` payloads — derived later in processor from `resourceIds` instead. See [07-flow-assignment-policy.md](07-flow-assignment-policy.md#sourcelead-id-lifecycle--important-nuance) | `FubWebhookParser` |
| 8 | Decision engine outcome-first ordering | Stale outcome labels can trigger tasks on connected calls | `CallDecisionEngine.decide` |
| 9 | `PolicyStepTransitionContractTest` incomplete | Only 3 of 6 transitions are tested. Missing: `NOT_CLAIMED`, `COMM_NOT_FOUND`, `ACTION_SUCCESS`. All 6 entries exist in `PolicyStepTransitionContract.TRANSITIONS` but the test class only covers `CLAIMED → NEXT`, `COMM_FOUND → TERMINAL`, `ACTION_FAILED → TERMINAL` | `PolicyStepTransitionContractTest` |
| 10 | No retry in ASSIGNMENT processor fan-out | ASSIGNMENT planning failures are logged but not retried. CALL domain uses exponential backoff. See [07-flow-assignment-policy.md](07-flow-assignment-policy.md#retry-behaviour--assignment-vs-call-domain) | `WebhookEventProcessorService.processAssignmentDomainEvent` |

---

## Verification Log (Document vs Code)

This document set was verified in 10 recursive passes against current branch code:

1. **Architecture and package structure** — validated against actual directory tree and class inventory (~125 Java classes).
2. **Webhook ingestion flow** — validated every step of `WebhookIngressService.ingest()` including source validation, payload size check, signature verification HMAC details, parser SHA-256 hash, two-stage duplicate detection, `DataIntegrityViolationException` catch, live feed error handling, and conditional dispatch.
3. **Call-domain flow** — validated `processCallDomainEvent` → `processCall` method chain including `getOrCreateEntity` race handling, `executeWithRetry` exponential backoff formula with jitter, `CallPreValidationService` rules, `CallDecisionEngine` 5-rule evaluation order, `CallbackTaskCommandFactory` task name mappings, dev guard logic, and all terminal state transitions.
4. **Assignment-domain planning** — validated `processAssignmentDomainEvent` fan-out, `PolicyExecutionManager.plan` with idempotency key SHA-256 construction, blueprint validation rules (7 checks), `getActivePolicy` lookup, step materialization with PENDING/WAITING_DEPENDENCY initial states, and `dueAt` calculation from blueprint `delayMinutes`.
5. **Due worker execution** — validated `pollAndProcessDueSteps` cycle/budget algorithm, `JdbcPolicyExecutionStepClaimRepository` SQL (`FOR UPDATE SKIP LOCKED`), `PolicyStepExecutionService.executeClaimedStep` method chain, and `compensateClaimedStepFailure` with `REQUIRES_NEW` and 3-attempt retry.
6. **Transition contract** — validated all 6 transition entries in `PolicyStepTransitionContract`, terminal transition logic (skip remaining steps, mark run COMPLETED), and next-step activation (WAITING_DEPENDENCY → PENDING with computed `dueAt`).
7. **Executor implementations** — validated all 3 executors: claim check (`claimed` field with `assignedUserId` fallback), communication check (derived from `contacted > 0`), action executor (validates action type + target and executes log-only adapter methods returning `ACTION_SUCCESS` in dev mode). Verified failure codes for invalid config/targets.
8. **FUB client adapter** — validated Basic Auth encoding, header construction, exception mapping (429/5xx → transient, 4xx → permanent, network → transient), `checkPersonCommunication` derivation from `getPersonById`, and `registerWebhook` stub status.
9. **Admin APIs** — validated all 12 endpoints with request/response shapes, cursor-based pagination (Base64 JSON encoding, keyset queries), JPA Specification filtering, replay behavior (field reset, synthetic event dispatch, retryCount TODO), policy CRUD with optimistic locking, activation with deactivation query, and SSE hub implementation (heartbeat, subscriber management, filter matching, event names).
10. **Database schema** — validated all 8 Flyway migrations (V1–V8) including table structures, column types, indexes, unique constraints, partial indexes, foreign keys, check constraints, and V8 identity resolver removal.

All flows, diagrams, method details, configuration values, and database schemas in this document set match the current branch code as of commit `83821d1`.
