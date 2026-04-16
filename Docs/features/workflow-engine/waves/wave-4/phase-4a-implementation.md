# Workflow Engine Rebuild — Wave 4a Implementation

## Status
Completed (Phases 1-4 completed, Wave 4a closed)

## Phase 1 Delivered — Foundations
- Schema:
  - Added Flyway migration:
    - [V11__add_workflow_version_number.sql](/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/resources/db/migration/V11__add_workflow_version_number.sql)
  - Added `automation_workflows.version_number INT NOT NULL DEFAULT 1`
  - Backfilled versions with `ROW_NUMBER() OVER (PARTITION BY key ORDER BY id)`
  - Added unique index `uk_automation_workflows_key_version_number` on `(key, version_number)`
  - Updated `chk_automation_workflows_status` to allow `ARCHIVED`
- Entity/enum:
  - `AutomationWorkflowEntity` now maps `versionNumber` (`@Column(name = "version_number", nullable = false)`)
  - Existing optimistic lock field `@Version version` kept unchanged
  - `WorkflowStatus` now includes `ARCHIVED`
  - `AutomationWorkflowEntity.onCreate()` sets default `versionNumber=1` when unset
- Trigger catalog SPI:
  - Added `WorkflowTriggerRegistry` (`get(id)`, `allTypes()`, duplicate-id detection)
  - Extended `WorkflowTriggerType` metadata contract with default methods:
    - `displayName()`
    - `description()`
    - `configSchema()`
  - `FubWebhookTriggerType` now provides concrete `description()`
  - `WorkflowTriggerRouter` refactored to depend on `WorkflowTriggerRegistry` instead of raw `List<WorkflowTriggerType>`
- Controller + DTO:
  - Added `TriggerTypeCatalogEntry`
  - Added endpoint `GET /admin/workflows/trigger-types` to `AdminWorkflowController`

## Tests Added/Updated
- Added:
  - `WorkflowTriggerRegistryTest`
- Updated:
  - `WorkflowTriggerRouterTest` (constructor wiring through registry, behavior assertions unchanged)
  - `AdminWorkflowControllerTest` (trigger-types catalog assertion)

## Validation Executed
- `./mvnw test -Dtest='WorkflowTriggerRegistryTest,AdminWorkflowControllerTest,WorkflowEngineSmokeTest'`
  - Result: passed (`Tests run: 15, Failures: 0, Errors: 0, Skipped: 6`)
- `./mvnw test`
  - Result: passed (`Tests run: 356, Failures: 0, Errors: 0, Skipped: 28`)

## Phase 2 Delivered — Workflow CRUD & Lifecycle
- Repository:
  - Added version-aware queries on `AutomationWorkflowRepository`:
    - latest by key (`findFirstByKeyOrderByVersionNumberDesc`)
    - all versions by key (`findByKeyOrderByVersionNumberDesc`)
    - lookup by key+version (`findByKeyAndVersionNumber`)
    - max version per key (`findMaxVersionNumberByKey`)
    - paged latest-per-key listing with optional status filter (`findLatestByStatusFilter`)
- Service:
  - Expanded `AutomationWorkflowService` with result-style APIs:
    - `update`, `rollback`, `activate`, `deactivate`, `archive`
    - `getLatestByKey`, `listVersions`, `list`
  - `update` and `rollback` append new versions (`max+1`) and force `INACTIVE`
  - lifecycle actions mutate only latest version status
  - list default excludes `ARCHIVED`; explicit status filter supported
- Controller + DTO:
  - Added DTOs:
    - `UpdateWorkflowRequest`
    - `WorkflowVersionSummary`
    - `PageResponse<T>`
    - `RollbackWorkflowRequest`
  - Extended `WorkflowResponse` with `versionNumber`
  - Added endpoints:
    - `GET /admin/workflows` (paged list)
    - `GET /admin/workflows/{key}` (latest by key)
    - `PUT /admin/workflows/{key}` (append new version)
    - `GET /admin/workflows/{key}/versions`
    - `POST /admin/workflows/{key}/activate`
    - `POST /admin/workflows/{key}/deactivate`
    - `POST /admin/workflows/{key}/rollback`
    - `DELETE /admin/workflows/{key}` (soft archive)
  - Kept compatibility route:
    - `GET /admin/workflows/by-id/{id}`

## Phase 2 Tests Added/Updated
- Added:
  - `AutomationWorkflowServiceTest`
- Updated:
  - `AdminWorkflowControllerTest` with create/update versioning, rollback, lifecycle, archive/list, pagination, key lookup, versions, and legacy by-id route coverage

## Phase 2 Validation Executed
- `./mvnw test -Dtest='AdminWorkflowControllerTest,AutomationWorkflowServiceTest,WorkflowGraphValidatorTest'`
  - Result: passed (`Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`)
- `./mvnw test`
  - Result: passed (`Tests run: 368, Failures: 0, Errors: 0, Skipped: 28`)

## Phase 3 Delivered — Validation Endpoint & Run Inspection
- Validation endpoint:
  - Added DTOs:
    - `ValidateWorkflowRequest(graph, trigger)`
    - `ValidateWorkflowResponse(valid, errors)`
  - Added `AutomationWorkflowService.validate(graph, trigger)`:
    - delegates graph checks to `WorkflowGraphValidator.validate(Map)`
    - validates `trigger.type` resolution via `WorkflowTriggerRegistry`
    - validates node `type` resolution via `WorkflowStepRegistry`
  - Added endpoint:
    - `POST /admin/workflows/validate`
    - returns `200` with structured `{valid, errors}` for validation outcomes
    - keeps plain-string `400` for malformed/missing request payload
- Run inspection:
  - Added `WorkflowRunQueryService` with:
    - `listRunsForKey(key, statusFilter, page, size)`
    - `listRunsCrossWorkflow(statusFilter, page, size)`
    - `getRunDetail(runId)`
  - Added repository paging queries on `WorkflowRunRepository`:
    - by workflow key with optional status filter
    - cross-workflow with optional status filter
  - Reused `WorkflowRunStepRepository.findByRunId` for step detail
  - Added DTOs:
    - `WorkflowRunSummary`
    - `WorkflowRunStepDetail`
    - `WorkflowRunDetailResponse`
  - Added `AdminWorkflowRunController` endpoints:
    - `GET /admin/workflows/{key}/runs`
    - `GET /admin/workflow-runs/{runId}`
    - `GET /admin/workflow-runs`

## Phase 3 Tests Added/Updated
- Added:
  - `AdminWorkflowRunControllerTest`
  - `WorkflowRunSummary`, `WorkflowRunStepDetail`, `WorkflowRunDetailResponse` DTO coverage via controller tests
- Updated:
  - `AdminWorkflowControllerTest` with `/admin/workflows/validate` scenarios:
    - valid graph/trigger
    - missing entry node
    - unknown step type
    - unknown trigger type
    - malformed payload

## Phase 3 Validation Executed
- `./mvnw test -Dtest='AdminWorkflowControllerTest'`
  - Result: passed (`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`)
- `./mvnw test -Dtest='AdminWorkflowControllerTest,AdminWorkflowRunControllerTest'`
  - Result: passed (`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`)
- `./mvnw test`
  - Result: passed (`Tests run: 378, Failures: 0, Errors: 0, Skipped: 28`)

## Phase 4 Delivered — Closing Fixes
- Root-cause fix (write semantics):
  - `WorkflowExecutionManager.plan()` now writes `workflow_runs.workflow_version` from append-only `AutomationWorkflowEntity.versionNumber` instead of JPA optimistic-lock `@Version`.
  - New mapping: `run.setWorkflowVersion(workflow.getVersionNumber() == null ? 1L : workflow.getVersionNumber().longValue())`.
- Read semantics alignment:
  - `WorkflowRunQueryService` now resolves `workflowVersionNumber` through a dedicated helper with explicit semantic comment and fallback `1L`.
  - `WorkflowStepExecutionService` now resolves `RunContext.RunMetadata.workflowVersion` through a matching helper with fallback `1L`.
  - Result: API responses and runtime metadata now consistently treat `workflow_runs.workflow_version` as append-only workflow version.
- HTTP consistency:
  - `POST /admin/workflows/{key}/rollback` now returns `200 OK` (aligned with `PUT /admin/workflows/{key}`).
  - `AdminWorkflowControllerTest` rollback assertion updated accordingly.
- Wave-gate integration test added:
  - New test: `WorkflowAdminApiIntegrationTest` (`@SpringBootTest`, `@AutoConfigureMockMvc`, Testcontainers Postgres, Docker-gated skip behavior).
  - Coverage includes:
    - step/trigger catalogs and validate endpoint
    - create/activate/update/rollback/deactivate/archive admin lifecycle
    - true in-flight snapshot guarantee (v1 run planned, v2 activated before v1 execution, v1 snapshot unchanged)
    - run version assertions on real runs (`workflowVersionNumber == 1`, then `2`, then `3`)
    - trigger catalog assertion locked to current id `webhook_fub`
  - Trigger seeding in the test was repository-based in the original Wave 4a delivery because create/update DTOs did not accept trigger input at that time.
- Idempotency pre-flight finding:
  - `WorkflowExecutionManager.buildIdempotencyKey()` input remains:
    - `workflowKey`, `source`, `sourceLeadId`, `eventId` (or fallback marker)
  - Neither `workflow.getVersion()` nor `workflow.getVersionNumber()` participates in idempotency hash input.
  - No idempotency-key algorithm changes were required for this fix.
- RunContext semantic clarification:
  - `RunContext.RunMetadata.workflowVersion` now carries append-only workflow version number semantics.
  - This is intentionally more informative than the prior optimistic-lock-derived value.
- Key-normalization centralization:
  - Added shared `KeyNormalizationHelper` and migrated workflow + policy key operations to use it.
  - Workflow keys now normalize trim-only at create/read/update/lifecycle boundaries, which closes finding #41 (create no longer persists raw spaced keys while reads trim).
  - `WorkflowExecutionManager` idempotency key composition now also uses helper-normalized workflow keys, preventing case-fold drift from create/read semantics.
  - Policy domain/policyKey normalization now reuses the same helper for consistent uppercase + length-guard behavior across policy services.

## Phase 4 Validation Executed
- `./mvnw test -Dtest='WorkflowAdminApiIntegrationTest'`
  - Result: passed with Docker-gated skip (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 1`)
- `./mvnw test -Dtest='WorkflowTriggerEndToEndTest,WorkflowEngineSmokeTest,WorkflowParityTest,AdminWorkflowControllerTest,AdminWorkflowRunControllerTest,AutomationWorkflowServiceTest'`
  - Result: passed (`Tests run: 43, Failures: 0, Errors: 0, Skipped: 14`)
- `./mvnw test -Dtest='KeyNormalizationHelperTest,WorkflowEngineSmokeTest'`
  - Result: passed with Docker-gated skip on Testcontainers smoke test (`Tests run: 12, Failures: 0, Errors: 0, Skipped: 7`)
- `./mvnw test`
  - Result: passed (`Tests run: 388, Failures: 0, Errors: 0, Skipped: 30`)

## Post-Close Stabilization (2026-04-15)
- Workflow worker runtime default:
  - `workflow.worker.enabled` default changed to `true` in `application.properties`.
  - `WORKFLOW_WORKER_ENABLED` override remains supported for explicit disable/enable by environment.
  - Updated `WorkflowWorkerPropertiesBindingTest` coverage to assert enabled-by-default and explicit disable override.
- Activation lifecycle safety:
  - `AutomationWorkflowService.activate()` now deactivates other active versions of the same workflow key before activating the latest version.
  - Added repository bulk update method `deactivateActiveWorkflowsByKeyExcludingId(...)` in `AutomationWorkflowRepository`.
  - Goal: satisfy `uk_automation_workflows_active_per_key` during version activation/rollback flows.
- Workflow worker smoke/parity tests:
  - `WorkflowEngineSmokeTest` and `WorkflowParityTest` now set:
    - `workflow.worker.enabled=true`
    - `spring.task.scheduling.enabled=false`
  - Goal: keep worker bean available for explicit `pollAndProcessDueSteps()` calls without background scheduler races.
- Policy stale-recovery Postgres test fixtures:
  - `PolicyExecutionStepClaimRepositoryPostgresTest` now backdates `updated_at` explicitly for PROCESSING rows before recovery assertions.
  - Goal: align test fixture setup with stale detection semantics (`updated_at <= staleBefore`) in `JdbcPolicyExecutionStepClaimRepository`.

## Post-Close Stabilization (2026-04-16)
- Workflow trigger authoring via admin create/update endpoints:
  - `CreateWorkflowRequest` now accepts `trigger` and `UpdateWorkflowRequest` now accepts optional `trigger`.
  - `POST /admin/workflows` now persists `trigger` when provided.
  - `PUT /admin/workflows/{key}` now applies a provided trigger override to the new version; if omitted, the latest version trigger is retained.
  - `AutomationWorkflowService` now validates provided `trigger.type` against `WorkflowTriggerRegistry` during create/update.
- Test coverage updates:
  - `AdminWorkflowControllerTest` now verifies create response includes persisted trigger and update can change trigger.
  - `WorkflowAdminApiIntegrationTest` now creates trigger-driven workflows directly via API payload (no repository trigger seeding helper).
  - `AutomationWorkflowServiceTest` now validates trigger persistence on create/update service paths.
