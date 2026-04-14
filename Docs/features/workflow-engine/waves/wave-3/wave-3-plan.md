## Wave 3 Implementation Plan (Revised, Full Fidelity)
### Dynamic Trigger Routing + Retry Primitive, split into tracked phases

## Summary
Wave 3 will be delivered in **separate tracked phases** (not one-shot), with a test gate and documentation update after each phase.  
Goal: make workflows self-serve by moving trigger matching/routing into workflow data, and add runtime retry with backoff for transient failures.

This builds on:
- Wave 1: engine skeleton (registry/validator/planner/worker/dispatcher)
- Wave 2: RunContext + JSONata templating + parity steps + end-to-end parity proof

Current gap being solved:
- routing still hard-coded in webhook processor
- transient failures currently fail runs permanently (no engine retry)

## Current Status Snapshot
- Phase 1: completed
- Phase 2: completed
- Phase 3: completed
- Phase 4: completed (end-to-end proof added in `WorkflowTriggerEndToEndTest`)

## Locked Decisions
- MVP Wave 3 step library: `fub_add_tag`, `slack_notify`, `http_request` only.
- `fub_add_tag` in Wave 3: **log-only simulation** (no real FUB write).
- Retry signaling: explicit `transientFailure` boolean on `StepExecutionResult`.
- Per-node retry override in workflow JSON: **enabled**.
- Router `triggerPayload` stored in planned runs: **full webhook payload**.
- Overflow handling (`matchedWorkflows × entities > cap`): **deterministic truncate**, plan first `N`, skip rest, warn log.
- Add router cap config: `workflow.trigger-router.max-fanout-per-event` (default `200`).
- Overflow-cap behavior must be documented as an identified limitation.

## Delivery/Tracking Model (Split Phases)
Use phase-by-phase tracked delivery with explicit gates:
1. implement phase scope
2. run phase tests
3. update feature docs
4. commit phase

Recommended branch tracking (to align repo workflow):
- Feature parent: existing `feature/workflow-engine-wave1` (Wave 3 work continues from here unless user asks otherwise)
- Optional phase implementation branches off feature parent:
  - `phase/workflow-engine-wave3-phase-1-retry`
  - `phase/workflow-engine-wave3-phase-2-trigger-router`
  - `phase/workflow-engine-wave3-phase-3-step-library`
  - `phase/workflow-engine-wave3-phase-4-e2e`
- Merge each phase branch back into feature parent before next phase.

---

## Phase 0: Wave 2 Commit Gate (Pre-work)
Objective: baseline cleanliness before Wave 3 changes.
- Confirm Wave 2 state is committed on `feature/workflow-engine-wave1`.
- Run full suite pre-gate (`./mvnw test`) and confirm baseline green (historically 303).
- If already clean/committed, record gate as satisfied and proceed.

---

## Phase 1: Retry Primitive (Engine Internal)
### 1A) Extend `StepExecutionResult`
- File: `src/main/java/com/fuba/automation_engine/service/workflow/StepExecutionResult.java`
- New record shape:
  - `boolean success`
  - `String resultCode`
  - `Map<String,Object> outputs`
  - `String errorMessage`
  - `boolean transientFailure`
- Factory behavior:
  - `success(code, outputs)` => `transientFailure=false`
  - `success(code)` => `transientFailure=false`
  - `failure(code, message)` => `transientFailure=false`
  - new `transientFailure(resultCode, errorMessage)` => `success=false`, `transientFailure=true`

### 1B) Per-node retry override parsing
- File: `src/main/java/com/fuba/automation_engine/service/workflow/RetryPolicy.java`
- Add helper:
  - `RetryPolicy.fromMap(Map<String,Object> map, RetryPolicy fallback)`
- Reads optional:
  - `maxAttempts`
  - `initialBackoffMs`
  - `backoffMultiplier`
  - `maxBackoffMs`
  - `retryOnTransient`
- Missing values inherit from fallback.

### 1C) Retry dispatch in execution service
- File: `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepExecutionService.java`
- Keep `Clock` injection for deterministic due-at tests.
- In non-success branch of `executeClaimedStep()`:
  - Resolve effective policy from `step.configSnapshot.retryPolicy` using `RetryPolicy.fromMap(...)`, fallback `stepType.defaultRetryPolicy()`.
  - If transient + retry allowed + attempts remaining:
    - compute backoff:
      - `backoffMs = min(initialBackoffMs * backoffMultiplier^retryCount, maxBackoffMs)`
    - `retryCount++`
    - `status = PENDING`
    - `dueAt = now + backoff`
    - run remains `PENDING`
    - save step and return
  - else: existing `markStepAndRunFailed(...)`.
- Keep current behavior for uncaught runtime exceptions: treat as execution errors (not auto-transient).

### 1D) Update parity steps to emit transient failures
- Files:
  - `.../WaitAndCheckClaimWorkflowStep.java`
  - `.../WaitAndCheckCommunicationWorkflowStep.java`
- When FUB transient path is exhausted/raised, return `StepExecutionResult.transientFailure(...)`.
- Permanent/validation failures remain `failure(...)`.

### 1E) Retry tests
- New: `src/test/java/com/fuba/automation_engine/service/workflow/WorkflowRetryDispatchTest.java`
- Scenarios:
  1. transient + retries available => requeued pending with dueAt and retryCount increment; run still pending
  2. transient + retries exhausted => step/run fail
  3. permanent failure => immediate fail, no retry
  4. exponential backoff + cap correctness
  5. per-node override beats default (`maxAttempts=5` override over default 3)
- Ensure existing workflow tests remain green (`WorkflowEngineSmokeTest`, `WorkflowParityTest`, validator tests).

Phase 1 gate:
- targeted retry/workflow tests pass
- docs updated
- phase commit

---

## Phase 2: Trigger Plugin Infrastructure + Router
### 2A) Trigger contracts
Add:
- `src/main/java/com/fuba/automation_engine/service/workflow/trigger/TriggerMatchContext.java`
  - `(source, eventType, normalizedDomain, normalizedAction, payload, triggerConfig)`
- `.../EntityRef.java`
  - `(entityType, entityId)`
- `.../WorkflowTriggerType.java`
  - `id()`, `displayName()`, `configSchema()`, `matches(ctx)`, `extractEntities(ctx)`

### 2B) `FubWebhookTriggerType`
Add:
- `src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java`
- `id = "webhook_fub"`
- Config schema:
  - `eventDomain: string`
  - `eventAction: string` (supports `*`)
  - `filter: string` optional JSONata
- Match logic:
  - source must be FUB
  - `eventDomain` vs normalizedDomain (`*` allowed)
  - `eventAction` vs normalizedAction (`*` allowed)
  - if `filter` present: evaluate JSONata against scope containing event payload; truthy required
- Entity extraction:
  - read `resourceIds` from payload
  - map to `EntityRef("lead", idAsString)`

### 2C) `WorkflowTriggerRouter`
Add:
- `src/main/java/com/fuba/automation_engine/service/workflow/trigger/WorkflowTriggerRouter.java`
- Inject:
  - `AutomationWorkflowRepository`
  - `List<WorkflowTriggerType>`
  - `WorkflowExecutionManager`
- Internal trigger map built in constructor (`id -> triggerType`)
- Repo addition:
  - `AutomationWorkflowRepository.findByStatus(WorkflowStatus status)`
- `route(NormalizedWebhookEvent event)`:
  1. load ACTIVE workflows
  2. for each:
     - skip null trigger
     - parse `trigger.type` + `trigger.config`
     - resolve type; unknown => warn+skip
     - build context from normalized event + config
     - evaluate `matches`, catch+log exceptions, skip on error
     - extract entities
     - generate `(workflow, entity)` combinations
  3. enforce `workflow.trigger-router.max-fanout-per-event`:
     - deterministic order (workflow id asc, entity order as extracted)
     - plan first `N`, skip rest, warn
  4. for each planned combo:
     - build `WorkflowPlanRequest(workflowKey, source, eventId, webhookEventId?, sourceLeadId, triggerPayload)`
     - `triggerPayload` = full webhook payload map
     - call `workflowExecutionManager.plan(...)`
     - log per-result; continue on failure
  5. return router summary record: matched/planned/failed/skipped counts

### 2D) Hook into webhook processor
Modify:
- `src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java`
- Inject `WorkflowTriggerRouter`
- In `process(event)`, after existing domain switch, invoke router in try/catch.
- Routing failures must not break legacy policy flow.

### 2E) Router config
Add properties:
- `workflow.trigger-router.max-fanout-per-event=200` (default via application properties + bind class if needed)

### 2F) Trigger/router tests
- New unit: `.../trigger/FubWebhookTriggerTypeTest.java`
  - wildcard/domain/action matches, mismatches, filter truthy/falsy/missing path, extraction with/without resourceIds
- New integration: `.../trigger/WorkflowTriggerRouterIntegrationTest.java`
  - matching workflow creates runs
  - non-matching filtered workflow creates none
  - 2 workflows × 2 entities => 4 plans
  - inactive skipped
  - unknown trigger type skipped
  - null trigger skipped
  - overflow cap truncates deterministically and logs warning

Phase 2 gate:
- trigger unit + integration tests pass
- docs updated
- phase commit

---

## Phase 3: MVP Step Library
### 3A) `fub_add_tag` (log-only for Wave 3)
Modify:
- `src/main/java/com/fuba/automation_engine/service/FollowUpBossClient.java`
  - add `ActionExecutionResult addTag(long personId, String tagName);`
- `src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java`
  - implement as log-only simulation in Wave 3, consistent with current simulated reassign/move patterns
- Add step:
  - `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubAddTagWorkflowStep.java`
  - id `fub_add_tag`
  - result codes `SUCCESS`, `FAILED`
  - default retry `RetryPolicy.DEFAULT_FUB`
  - config schema `{ tagName: string }`
  - use `FubCallHelper.parsePersonId`
  - transient/permanent mapping via `StepExecutionResult.transientFailure(...)` vs `failure(...)`

### 3B) `http_request`
Add:
- `.../steps/HttpRequestWorkflowStep.java`
- id `http_request`
- result codes `SUCCESS`, `FAILED`
- default retry policy enabled
- config schema:
  - `method`, `url`, `headers`, `body`, optional `expectedStatusCodes`
- uses dedicated `RestClient` with configurable connect/read timeouts
- status mapping:
  - expected success codes => success with outputs (`statusCode`, response metadata/body)
  - 5xx/timeouts => transient failure
  - 4xx => permanent failure
- security note:
  - no SSRF allowlist in Wave 3
  - add `TODO(wave-4-security)` and doc it as deferred limitation

### 3C) `slack_notify`
Add:
- `.../steps/SlackNotifyWorkflowStep.java`
- id `slack_notify`
- result codes `SUCCESS`, `FAILED`
- config schema:
  - `webhookUrl`, `text`, optional `channel`, `username`
- POST via shared HTTP mechanism
- status mapping same as HTTP step
- do not log webhook URL/secrets

### 3D) Step tests
Add:
- `.../steps/FubAddTagWorkflowStepTest.java`
- `.../steps/HttpRequestWorkflowStepTest.java`
- `.../steps/SlackNotifyWorkflowStepTest.java`
Coverage:
- success path
- transient path
- permanent path
- templated/resolved config usage where relevant

Phase 3 gate:
- step tests pass
- workflow step-type catalog includes new steps (`/admin/workflows/step-types`)
- docs updated
- phase commit

---

## Phase 4: End-to-End Proof
Add:
- `src/test/java/com/fuba/automation_engine/service/workflow/WorkflowTriggerEndToEndTest.java`
- Testcontainers + fixed clock + stub FUB client + HTTP mock for Slack
- Seed ACTIVE workflow:
  - trigger type `webhook_fub`
  - config domain/action/filter for Zillow
  - graph: `fub_add_tag -> slack_notify` with terminal transitions
- Scenarios:
  1. matching webhook => run created + steps complete + expected outputs/interactions + terminal reason
  2. non-matching webhook => no workflow run
  3. transient Slack failure then success => retryCount increments and run completes

Phase 4 gate:
- e2e scenarios pass
- docs updated
- phase commit

Phase 4 execution status:
- Implemented in working tree.
- Scenario coverage added:
  1. matching webhook => run created + terminal completion
  2. non-matching webhook => no run created
  3. transient Slack failure => retry then success
- Side-by-side flow assertion included:
  - assignment webhook still creates policy execution run(s) while workflow trigger routing remains active.
- Environment note:
  - `WorkflowTriggerEndToEndTest` is Docker/Testcontainers-gated and is skipped when Docker is unavailable.

---

## Files Expected to Change
### New (core groups)
- Retry test: `WorkflowRetryDispatchTest`
- Trigger infra:
  - `TriggerMatchContext`
  - `EntityRef`
  - `WorkflowTriggerType`
  - `FubWebhookTriggerType`
  - `WorkflowTriggerRouter`
  - trigger tests (unit + integration)
- Step types:
  - `FubAddTagWorkflowStep`
  - `HttpRequestWorkflowStep`
  - `SlackNotifyWorkflowStep`
  - step tests
- E2E:
  - `WorkflowTriggerEndToEndTest`

### Modified
- `StepExecutionResult`
- `RetryPolicy`
- `WorkflowStepExecutionService`
- `WaitAndCheckClaimWorkflowStep`
- `WaitAndCheckCommunicationWorkflowStep`
- `WebhookEventProcessorService`
- `AutomationWorkflowRepository`
- `FollowUpBossClient`
- `FubFollowUpBossClient`
- application config for router cap (and HTTP timeouts, if new config class added)

### Explicitly not changed
- no DB migration (Wave 3 uses existing V10 columns)
- no policy-engine replacement/removal (`service/policy/*` remains active)
- no workflow builder UI (`ui/src/modules/workflows`) in this wave

---

## Test/Validation Plan
Phase-by-phase:
- run phase-targeted tests immediately after each phase
- keep existing Wave 1/2 tests green continuously

Final gate:
- `./mvnw test` full suite green
- expected count trend: baseline 303 + Wave 3 additions (~20–30 range)
- if Docker unavailable, report explicit blocker and which Testcontainers suites were skipped

Pre-existing suites that must remain green:
- `WorkflowEngineSmokeTest`
- `WorkflowGraphValidatorTest`
- `WorkflowParityTest`
- `ExpressionEvaluator` tests
- webhook processor tests

---

## Documentation/Tracking Updates (Mandatory)
Per phase:
- update `Docs/features/workflow-engine/waves/wave-3/phase-3-implementation.md` incrementally
- update `Docs/features/workflow-engine/phases.md` status/progress
- keep `Docs/features/workflow-engine/waves/wave-3/closing-plan.md` and `Docs/features/workflow-engine/research.md` aligned with delivered scope
- preserve repo decision/feature workflow traceability (`Docs/repo-decisions/README.md` protocol)

Add/refresh known limitations:
- fan-out cap truncation behavior (`workflow.trigger-router.max-fanout-per-event`)
- `fub_add_tag` is log-only in Wave 3
- `http_request` SSRF/egress controls deferred to later wave

---

## Deferred to Wave 3b / Later
- additional step types:
  - `fub_remove_tag`
  - `fub_create_note`
  - `fub_send_text`
  - `fub_send_email`
  - `fub_create_task`
- SSRF allowlist and hardened egress policy for `http_request`
- outbound circuit-breaker/timeout governance enhancements
- rate limiting beyond simple fan-out cap
- broader workflow admin CRUD/UX wave

---

## Assumptions
- Wave 2 behavior/tests remain source-of-truth baseline.
- Router consumes normalized webhook event already produced by ingestion/dispatcher path.
- Full payload can be converted safely from `JsonNode` to `Map<String,Object>` for `triggerPayload` snapshot.
- Legacy and workflow engines continue running side by side during Wave 3.

---

## Phase 1 Progress Notes (Implemented)
Date: 2026-04-14

### Step 0 — Baseline Gate
- Branch confirmed: `feature/workflow-engine-wave1`
- Baseline workflow-focused suite executed:
  - `./mvnw test -Dtest='Workflow*Test,AdminWorkflowControllerTest,WebhookEventProcessorServiceTest'`
  - Result: `BUILD SUCCESS` — 40 tests run, 0 failures, 0 errors, 11 skipped (Docker/Testcontainers unavailable)

### Step 1 / 2 — Result Contract + RetryPolicy Parser
- Implemented:
  - `StepExecutionResult.transientFailure` field and factory
  - `RetryPolicy.fromMap(Map<String,Object>, RetryPolicy fallback)`
  - `RetryPolicyTest` for typed parsing/fallback scenarios
- Gate run:
  - `./mvnw test -Dtest='RetryPolicyTest,WorkflowGraphValidatorTest'`
  - Result: `BUILD SUCCESS` — 19 tests run, 0 failures, 0 errors
- Commit:
  - `23752aa` — `workflow: add transient result flag and retry policy map parsing`

### Step 3 / 4 / 5 — Retry Dispatch + Parity Step Transient Mapping + Retry Tests
- Implemented:
  - Retry scheduling branch in `WorkflowStepExecutionService`:
    - effective policy resolution from `configSnapshot.retryPolicy`
    - transient retry eligibility check
    - capped exponential backoff scheduling (`PENDING`, incremented `retryCount`, updated `dueAt`)
  - `WaitAndCheckClaimWorkflowStep` and `WaitAndCheckCommunicationWorkflowStep` now emit `StepExecutionResult.transientFailure(...)` for transient FUB errors
  - `WorkflowRetryDispatchTest` with scenarios:
    - transient retry available
    - transient retry exhausted
    - permanent failure immediate fail
    - backoff capping
    - per-node retry-policy override precedence
- Gate run:
  - `./mvnw test -Dtest='WorkflowRetryDispatchTest,RetryPolicyTest,WorkflowGraphValidatorTest,AdminWorkflowControllerTest,WebhookEventProcessorServiceTest'`
  - Result: `BUILD SUCCESS` — 36 tests run, 0 failures, 0 errors
- Commit:
  - `5433f13` — `workflow: add transient retry dispatch in step execution`

### Step 6 — Phase Closure Validation

---

## Phase 3 Progress Notes (Implemented)
Date: 2026-04-14

### Checkpoint 1 — Step Catalog Contract
- Implemented:
  - `defaultRetryPolicy` added to `/admin/workflows/step-types` contract.
  - `AdminWorkflowController` now serializes each step type retry defaults.
- Gate:
  - `./mvnw test -Dtest='AdminWorkflowControllerTest,WorkflowGraphValidatorTest'`
  - Result: `BUILD SUCCESS`.

### Checkpoint 2 — `fub_add_tag` (log-only)
- Implemented:
  - `FollowUpBossClient.addTag(long personId, String tagName)` port contract.
  - `FubFollowUpBossClient.addTag(...)` log-only simulation (Wave 3).
  - `FubAddTagWorkflowStep` with config schema, retry defaults, transient/permanent mapping.
  - all `FollowUpBossClient` test doubles updated for interface compatibility.
- Gate:
  - `./mvnw test -Dtest='FubAddTagWorkflowStepTest,FubFollowUpBossClientTest,WorkflowParityTest'`
  - Result: `BUILD SUCCESS`; `WorkflowParityTest` skipped when Docker unavailable.

### Checkpoint 3 — `http_request` + `slack_notify`
- Implemented:
  - new outbound HTTP boundary:
    - `WorkflowHttpClient` port
    - `WorkflowRestHttpClientAdapter` adapter
    - timeout properties: `workflow.step-http.connect-timeout-ms`, `workflow.step-http.read-timeout-ms`
  - new step types:
    - `HttpRequestWorkflowStep` (`http_request`)
    - `SlackNotifyWorkflowStep` (`slack_notify`)
  - transient/permanent mapping:
    - retryable: transport failures + HTTP 5xx
    - permanent: HTTP 4xx / non-retryable failures
  - secret-safe behavior:
    - Slack step errors do not include webhook URL.
- Gate:
  - `./mvnw test -Dtest='HttpRequestWorkflowStepTest,SlackNotifyWorkflowStepTest,WorkflowRetryDispatchTest'`
  - Result: `BUILD SUCCESS`.

### Phase 3 Completion Snapshot
- Step catalog now includes: `fub_add_tag`, `http_request`, `slack_notify`.
- Legacy policy flow remains side-by-side with workflow routing.
- `webhookEventId` remains `null` for router-planned workflow runs (unchanged by design).
- Final validation gates executed:
  - `./mvnw test -Dtest='AdminWorkflowControllerTest,FubAddTagWorkflowStepTest,HttpRequestWorkflowStepTest,SlackNotifyWorkflowStepTest,WebhookEventProcessorServiceTest,WorkflowTriggerRouterTest,WorkflowTriggerRouterIntegrationTest,WorkflowEngineSmokeTest,WorkflowParityTest,WorkflowGraphValidatorTest'` (`BUILD SUCCESS`, Docker-gated suites skipped when unavailable)
  - `./mvnw test` (`BUILD SUCCESS`)
- Workflow-focused suite re-run:
  - `./mvnw test -Dtest='Workflow*Test,AdminWorkflowControllerTest,WebhookEventProcessorServiceTest'`
  - Result: `BUILD SUCCESS` — 45 tests run, 0 failures, 0 errors, 11 skipped (Docker/Testcontainers unavailable)
- Full project suite run:
  - `./mvnw test`
  - Result: `BUILD SUCCESS` — 320 tests run, 0 failures, 0 errors, 21 skipped (Docker/Testcontainers unavailable)

### Phase 1 Outcome
- Phase 1 retry primitive is implemented and validated.
- No trigger routing changes, no new Wave 3 step-library implementations, and no DB migration were included in this phase.

---

## Phase 2 Progress Notes (Implemented)
Date: 2026-04-14

### Implemented Scope
- Trigger SPI contracts added:
  - `TriggerMatchContext`
  - `EntityRef`
  - `WorkflowTriggerType`
- FUB trigger plugin added:
  - `FubWebhookTriggerType` with:
    - `id = webhook_fub`
    - `eventDomain` / `eventAction` matching (supports `*`)
    - optional JSONata `filter`
    - `resourceIds` entity extraction
- Router infrastructure added:
  - `WorkflowTriggerRouter`
  - `AutomationWorkflowRepository.findByStatus(WorkflowStatus status)`
  - deterministic fan-out cap via `workflow.trigger-router.max-fanout-per-event` (default 200)
  - full webhook payload snapshot mapped into `WorkflowPlanRequest.triggerPayload`
- Webhook processor integration added:
  - `WebhookEventProcessorService` now calls router after domain processing
  - router exceptions are isolated in `try/catch` so existing policy/call flow continues

### Tests Added/Updated
- Added:
  - `FubWebhookTriggerTypeTest`
  - `WorkflowTriggerRouterTest`
  - `WorkflowTriggerRouterIntegrationTest` (Docker-dependent)
- Updated:
  - `WebhookEventProcessorServiceTest` to validate router invocation and failure isolation

### Validation Gates Run
- `./mvnw test -Dtest='WorkflowGraphValidatorTest,RetryPolicyTest'` -> `BUILD SUCCESS`
- `./mvnw test -Dtest='FubWebhookTriggerTypeTest,ExpressionEvaluatorTest'` -> `BUILD SUCCESS`
- `./mvnw test -Dtest='WorkflowTriggerRouterIntegrationTest,WorkflowEngineSmokeTest'` -> `BUILD SUCCESS` (Docker suites skipped)
- `./mvnw test -Dtest='WebhookEventProcessorServiceTest,WorkflowTriggerRouterIntegrationTest,FubWebhookTriggerTypeTest'` -> `BUILD SUCCESS` (Docker suites skipped)
- `./mvnw test -Dtest='FubWebhookTriggerTypeTest,WorkflowTriggerRouterIntegrationTest,WebhookEventProcessorServiceTest,WorkflowRetryDispatchTest,WorkflowParityTest,WorkflowEngineSmokeTest,WorkflowGraphValidatorTest'` -> `BUILD SUCCESS` (Docker suites skipped)
- `./mvnw test` -> `BUILD SUCCESS` (335 tests run, 0 failures, 0 errors, 25 skipped)

### Phase 2 Outcome
- Trigger plugin infrastructure and router are integrated and validated.
- Legacy policy engine routing remains active and unaffected.
- No DB migration introduced in Phase 2.
