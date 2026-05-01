# Workflow Engine Rebuild — Wave 4a (Admin REST API) Implementation Plan

## Status
Planned (not yet started). Target branch: phase branch off `feature/workflow-engine-wave1`.

## Context

Waves 1–3 delivered the execution core: plugin step/trigger registries, `WorkflowExecutionManager.plan()` with SHA256 idempotency, a due-worker driven by `FOR UPDATE SKIP LOCKED` claims, JSONata templating via `RunContext`, engine-level retry dispatch, and `WorkflowTriggerRouter` that routes live FUB webhooks to active workflows. What is missing is any human-facing way to drive the engine. `AdminWorkflowController` currently exposes only three endpoints (create, get-by-id, step-types catalog) and `AutomationWorkflowService` has only `create`, `getById`, and `getActiveByKey`. There is no update path, no lifecycle control, no run inspection, no rollback, and no trigger-type catalog.

Wave 4a closes that gap with a complete read/write admin REST API covering the full workflow lifecycle and read-only run inspection. This is the last backend dependency for Wave 4b (builder UI). Operator controls (cancel/retry) are deferred to Wave 4c. The wave succeeds when a developer can — using HTTP alone — list catalogs, validate a workflow, create and activate it, fire a webhook, inspect the resulting run, update the workflow creating a new version, roll it back if broken, and deactivate it, with the existing 353-test suite still green.

## Locked Decisions

| Decision | Choice |
|---|---|
| Versioning model | Append-only rows per `key` with explicit `version_number` column. Update = INSERT new row at `max+1`. Rollback = re-insert a prior version's graph as new latest. |
| Run controls in 4a | None. Read-only inspection only. Cancel/retry deferred to Wave 4c. |
| Error response shape | Plain-string `ResponseEntity.body("...")` with HTTP status codes. Matches `AdminPolicyController`. |
| Pagination | Simple offset/limit (`?page=0&size=20`). New generic `PageResponse<T>(items, page, size, total)` record. |
| Auth | None. Matches existing `/admin/*` posture (no `@PreAuthorize` or filters anywhere). |
| Trigger catalog exposure | New `WorkflowTriggerRegistry` bean modeled on `WorkflowStepRegistry`; `WorkflowTriggerRouter` refactored to consume it. |
| Soft delete | New `WorkflowStatus.ARCHIVED`. Hard delete rejected — `workflow_runs` FKs depend on history. |
| Test posture | `@WebMvcTest` slice tests for new controllers; one `@SpringBootTest` end-to-end integration test gates the wave. |

## Phased Scope

### Phase 1 — Foundations (schema, enum, trigger catalog)

Groundwork everything else depends on. Self-contained and shippable independently if needed.

- Schema:
  - new Flyway migration `V{next}__add_workflow_version_number.sql`
  - `ALTER TABLE automation_workflows ADD COLUMN version_number INT NOT NULL DEFAULT 1`
  - backfill existing rows via `ROW_NUMBER() OVER (PARTITION BY key ORDER BY id)`
  - unique index on `(key, version_number)`
- Entity/enum:
  - `AutomationWorkflowEntity`: new `versionNumber` int field (keep `@Version` untouched — harmless on append-only)
  - `WorkflowStatus`: add `ARCHIVED` value
  - audit every `switch` / `if` over `WorkflowStatus` — confirm `ARCHIVED` is treated as not-runnable in `WorkflowTriggerRouter`, `WorkflowExecutionDueWorker`, `WorkflowStepExecutionService`, `AutomationWorkflowService.getActiveByKey`
- Trigger catalog SPI:
  - new `WorkflowTriggerRegistry` bean modeled on `WorkflowStepRegistry` (duplicate-id detection, `get(id)`, `allTypes()`)
  - `WorkflowTriggerType`: add `displayName()`, `description()`, `configSchema()` methods to parallel `WorkflowStepType`
  - `FubWebhookTriggerType`: implement the new methods
  - `WorkflowTriggerRouter`: switch from raw `List<WorkflowTriggerType>` injection to `WorkflowTriggerRegistry` (behavioral no-op — consolidates lookup)
- Controller + DTO:
  - new `TriggerTypeCatalogEntry` record (mirrors `StepTypeCatalogEntry`)
  - new endpoint `GET /admin/workflows/trigger-types` on `AdminWorkflowController`

Tests:
- new `WorkflowTriggerRegistryTest` (unit) — duplicate detection, lookup round-trip
- extend `AdminWorkflowControllerTest` with a trigger-types catalog assertion
- full suite must remain green after migration + enum fan-out

### Phase 2 — Workflow CRUD & Lifecycle

The heart of the wave. All workflow write and read-by-key operations.

- Repository additions on `AutomationWorkflowRepository`:
  - `findFirstByKeyOrderByVersionNumberDesc(String key)`
  - `findByKeyOrderByVersionNumberDesc(String key)`
  - `findByKeyAndVersionNumber(String key, int versionNumber)`
  - `findMaxVersionNumberByKey(String key)` via `@Query("SELECT MAX(...)")`
  - paged list query (latest version per key, optional status filter)
- Service additions on `AutomationWorkflowService` (following existing `CreateResult` pattern: status enum + entity + error string):
  - `update(key, name, description, graph) → UpdateResult` — validates via `WorkflowGraphValidator`, INSERTs new row at `max+1`, forces status=INACTIVE regardless of prior state
  - `activate(key) → ActivateResult` — sets latest version to ACTIVE
  - `deactivate(key) → DeactivateResult` — sets latest version to INACTIVE; in-flight runs continue via `workflowGraphSnapshot`
  - `rollback(key, toVersionNumber) → UpdateResult` — reads target version's graph, INSERTs at `max+1`, status=INACTIVE
  - `archive(key) → ArchiveResult` — sets latest version to ARCHIVED (soft delete)
  - `listVersions(key) → List<AutomationWorkflowEntity>`
  - `getLatestByKey(key) → Optional<AutomationWorkflowEntity>`
  - `list(statusFilter, page, size) → PageResult<AutomationWorkflowEntity>`
- New DTOs under `controller/dto/`:
  - `UpdateWorkflowRequest(name, description, graph)` record
  - `WorkflowVersionSummary(versionNumber, status, createdAt, updatedAt)` record
  - generic `PageResponse<T>(items, page, size, total)` record
  - extend existing `WorkflowResponse` with `versionNumber` and `trigger` if not already present
- Controller endpoints on `AdminWorkflowController`:
  - `GET /admin/workflows` — paged list, `?status=&page=&size=`
  - `GET /admin/workflows/{key}` — **replaces** the existing id-based `{id}` endpoint. Grep first for callers; if any exist, park the old endpoint at `/admin/workflows/by-id/{id}`
  - `PUT /admin/workflows/{key}` — creates new version
  - `GET /admin/workflows/{key}/versions`
  - `POST /admin/workflows/{key}/activate`
  - `POST /admin/workflows/{key}/deactivate`
  - `POST /admin/workflows/{key}/rollback` — body `{toVersion: N}`
  - `DELETE /admin/workflows/{key}` — soft archive

Tests:
- extend `AdminWorkflowControllerTest` (`@SpringBootTest` style, real DB) with:
  - create → update → assert two rows, `version_number` 1 and 2
  - rollback v2 → v1 → assert new row at version 3 with v1's graph
  - activate / deactivate transitions
  - archive soft-delete excluded from default list
  - pagination (`?page=&size=`) over a seeded set
- `AutomationWorkflowServiceTest` (unit) with mocked repo — rollback-of-nonexistent-version error path, update-of-nonexistent-key error path
- assertion that in-flight runs from v1 keep using the v1 `workflowGraphSnapshot` after v2 activation

### Phase 3 — Validation Endpoint & Run Inspection

Read-only inspection surface plus a dry-run validator.

- Validation endpoint:
  - new DTOs `ValidateWorkflowRequest(graph, trigger)` and `ValidateWorkflowResponse(valid, errors)`
  - new method `AutomationWorkflowService.validate(graph, trigger)` that delegates to the existing `WorkflowGraphValidator.validate(Map)` (no change to the validator) and additionally checks:
    - `trigger.type` resolves in `WorkflowTriggerRegistry`
    - every node's `stepType` resolves in `WorkflowStepRegistry`
  - new endpoint `POST /admin/workflows/validate` on `AdminWorkflowController`
- Repository additions:
  - `WorkflowRunRepository`: paged query for runs by workflow key across all versions (join to `AutomationWorkflowEntity.key`); optional cross-workflow paged list with status filter
  - confirm existing `WorkflowRunStepRepository.findByRunId` is sufficient for step detail
- New query service:
  - `WorkflowRunQueryService` (new class — keeps `AutomationWorkflowService` focused on write/lifecycle)
  - methods: `listRunsForKey(key, statusFilter, page, size)`, `listRunsCrossWorkflow(statusFilter, page, size)`, `getRunDetail(runId) → Optional<RunDetail>`
  - `getRunDetail` does one `WorkflowRunRepository.findById` + one `WorkflowRunStepRepository.findByRunId` — no N+1
- New run-inspection DTOs:
  - `WorkflowRunSummary(id, workflowKey, workflowVersionNumber, status, reasonCode, startedAt, completedAt)` record
  - `WorkflowRunStepDetail(id, nodeId, stepType, status, resultCode, outputs, errorMessage, retryCount, dueAt, startedAt, completedAt)` record
  - `WorkflowRunDetailResponse(summary fields, triggerPayload, sourceLeadId, eventId, steps)` record
- New controller (split from `AdminWorkflowController` for size):
  - `AdminWorkflowRunController`:
    - `GET /admin/workflows/{key}/runs` — paged `PageResponse<WorkflowRunSummary>`
    - `GET /admin/workflow-runs/{runId}` — detail with steps inlined
    - `GET /admin/workflow-runs` — optional cross-workflow paged list for debugging

Tests:
- slice test `AdminWorkflowControllerTest` validate-endpoint cases:
  - valid graph + trigger → 200 `{valid: true, errors: []}`
  - missing start node → 200 `{valid: false, errors: [...]}`
  - unknown step type → error
  - unknown trigger type → error
- new slice test `AdminWorkflowRunControllerTest` (`@WebMvcTest` + mocked `WorkflowRunQueryService`) — list-for-key paging, detail inlines steps, 404 on unknown runId, cross-workflow list status filter

### Phase 4 — End-to-End Proof

Wave gate. Exercises every endpoint against a real database plus the Wave 3 trigger router.

- New integration test `WorkflowAdminApiIntegrationTest` (`@SpringBootTest` + Testcontainers Postgres, deterministic clock, reuses Wave 3 webhook harness):
  - `GET /admin/workflows/step-types` and `/trigger-types` return the expected catalogs
  - `POST /admin/workflows/validate` round-trips
  - `POST /admin/workflows` + `POST /{key}/activate` → ACTIVE v1
  - fire a matching FUB webhook → run planned via `WorkflowTriggerRouter`
  - `GET /admin/workflows/{key}/runs` returns the new run
  - `GET /admin/workflow-runs/{runId}` returns step-level detail
  - `PUT /admin/workflows/{key}` → v2 created, v1 run's `workflowGraphSnapshot` unchanged
  - activate v2 → new webhook plans run against v2
  - `POST /admin/workflows/{key}/rollback {toVersion:1}` → v3 with v1's graph
  - `POST /admin/workflows/{key}/deactivate` → subsequent webhook plans no run
  - assertion: pre-existing 353 tests still green (no regressions in trigger router, due-worker, retry dispatch, parity suite)

## Files to Create

- `src/main/resources/db/migration/V{next}__add_workflow_version_number.sql`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowTriggerRegistry.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowRunQueryService.java`
- `src/main/java/com/fuba/automation_engine/controller/AdminWorkflowRunController.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/TriggerTypeCatalogEntry.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/UpdateWorkflowRequest.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/WorkflowVersionSummary.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/PageResponse.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/ValidateWorkflowRequest.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/ValidateWorkflowResponse.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/WorkflowRunSummary.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/WorkflowRunStepDetail.java`
- `src/main/java/com/fuba/automation_engine/controller/dto/WorkflowRunDetailResponse.java`
- `src/test/java/com/fuba/automation_engine/service/workflow/WorkflowTriggerRegistryTest.java`
- `src/test/java/com/fuba/automation_engine/controller/AdminWorkflowRunControllerTest.java`
- `src/test/java/com/fuba/automation_engine/controller/WorkflowAdminApiIntegrationTest.java`

## Files to Modify

- `src/main/java/com/fuba/automation_engine/persistence/entity/AutomationWorkflowEntity.java` — add `versionNumber`
- `src/main/java/com/fuba/automation_engine/persistence/entity/WorkflowStatus.java` — add `ARCHIVED`
- `src/main/java/com/fuba/automation_engine/persistence/repository/AutomationWorkflowRepository.java` — add version-aware finders + paged list
- `src/main/java/com/fuba/automation_engine/persistence/repository/WorkflowRunRepository.java` — add paged queries (by key, cross-workflow)
- `src/main/java/com/fuba/automation_engine/service/workflow/AutomationWorkflowService.java` — add update/activate/deactivate/rollback/archive/list/listVersions/getLatestByKey/validate
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowTriggerType.java` — add display metadata methods
- `src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java` — implement display metadata
- `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowTriggerRouter.java` — consume `WorkflowTriggerRegistry`
- `src/main/java/com/fuba/automation_engine/controller/AdminWorkflowController.java` — add list/update/versions/activate/deactivate/rollback/archive/validate/trigger-types endpoints; migrate `{id}` → `{key}` path
- `src/main/java/com/fuba/automation_engine/controller/dto/WorkflowResponse.java` — extend with `versionNumber`, `trigger`
- `src/test/java/com/fuba/automation_engine/controller/AdminWorkflowControllerTest.java` — cover every new endpoint
- `src/test/java/com/fuba/automation_engine/service/workflow/AutomationWorkflowServiceTest.java` — cover new service error paths

## Reused Utilities (do NOT reimplement)

- `WorkflowGraphValidator.validate(Map)` at `service/workflow/WorkflowGraphValidator.java` — returns `GraphValidationResult`, no persistence; validate endpoint delegates directly
- `WorkflowStepRegistry` at `service/workflow/WorkflowStepRegistry.java` — template for the new `WorkflowTriggerRegistry` AND source of truth for step-type presence checks in the validator
- `StepTypeCatalogEntry` DTO — step-types catalog endpoint stays unchanged
- `AutomationWorkflowRepository.findFirstByKeyAndStatusOrderByIdDesc` — trigger router keeps using existing finder; optionally converge to the new version-aware finder in Phase 2 for consistency
- `WorkflowRunStepRepository.findByRunId` — drives `getRunDetail`'s step fetch
- Existing Wave 3 FUB webhook test harness — reused by Phase 4 end-to-end test

## Verification

Per-phase Maven invocations (mirrors Phase 3 validation pattern):

```
# Phase 1
./mvnw test -Dtest='WorkflowTriggerRegistryTest,AdminWorkflowControllerTest'

# Phase 2
./mvnw test -Dtest='AdminWorkflowControllerTest,AutomationWorkflowServiceTest,WorkflowGraphValidatorTest'

# Phase 3
./mvnw test -Dtest='AdminWorkflowControllerTest,AdminWorkflowRunControllerTest'

# Phase 4 (wave gate)
./mvnw test -Dtest='WorkflowAdminApiIntegrationTest'
./mvnw test
```

Expected outcome: full-suite snapshot remains `Tests run: 353+new, Failures: 0, Errors: 0, Skipped: 28` (Docker-gated suites still skip when Testcontainers unavailable).

Smoke curl sequence against a running instance (documents success criteria concretely):

```
curl localhost:8080/admin/workflows/step-types
curl localhost:8080/admin/workflows/trigger-types
curl -X POST localhost:8080/admin/workflows/validate -d '{"graph": {...}, "trigger": {...}}'
curl -X POST localhost:8080/admin/workflows -d '{"key":"test-wf", ...}'
curl -X POST localhost:8080/admin/workflows/test-wf/activate
curl -X POST localhost:8080/webhooks/fub -d '{...}'   # reuse Wave 3 webhook path
curl localhost:8080/admin/workflows/test-wf/runs
curl localhost:8080/admin/workflow-runs/{runId}
curl -X PUT localhost:8080/admin/workflows/test-wf -d '{...}'
curl -X POST localhost:8080/admin/workflows/test-wf/activate
curl -X POST localhost:8080/admin/workflows/test-wf/rollback -d '{"toVersion":1}'
curl -X POST localhost:8080/admin/workflows/test-wf/deactivate
```

## Risks / Things to Double-Check During Implementation

1. **`GET /admin/workflows/{id}` → `{key}` path collision.** Existing endpoint takes `Long id`. Grep for consumers before renaming; park old endpoint at `/admin/workflows/by-id/{id}` if any exist.
2. **Flyway backfill idempotency.** The `version_number` backfill must be safe against a populated DB. Verify on a cloned snapshot before merging.
3. **`WorkflowStatus.ARCHIVED` fan-out.** New enum values require auditing every `switch`/`if` over the enum. Specifically: `WorkflowTriggerRouter` must not route archived workflows; due-worker continues executing in-flight runs (safe via snapshot); `getActiveByKey` already filters to ACTIVE.
4. **Run listing across versions.** Filtering runs by key must return runs from v1 and v2 together. `WorkflowRunSummary` must include `workflowVersionNumber` so operators can distinguish them.
5. **`@Version` field on entity.** Unused under append-only but safe to leave. Do not remove — schema migration risk with zero benefit.
6. **Service result records vs exceptions.** Follow the existing `CreateResult` pattern. Do not introduce a new exception hierarchy for workflow CRUD.
7. **Controller size.** Run inspection goes in a second controller (`AdminWorkflowRunController`) to keep files manageable.

## Explicit Deferrals

- **Wave 4b** — builder UI (entire frontend).
- **Wave 4c** — operator controls: `POST /admin/workflow-runs/{id}/cancel`, `POST /admin/workflow-runs/{id}/retry`, `POST /admin/workflow-run-steps/{id}/retry`.
- **Separate wave** — auth/RBAC on `/admin/*` (codebase-wide concern, not workflow-specific).
- **Separate wave** — dry-run template evaluation inside the validate endpoint.
- **Separate wave** — SSRF/egress allowlisting for `http_request`/`slack_notify` (inherited Wave 3 limitation).
