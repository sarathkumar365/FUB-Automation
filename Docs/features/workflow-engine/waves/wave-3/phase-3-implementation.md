# Workflow Engine Rebuild — Phase 3 Implementation

## Status
Completed (Wave 3 Phases 1-4 completed in working tree)

## Scope Delivered
- Phase 1 (retry primitive):
  - transient failure signaling in `StepExecutionResult`
  - per-node retry override parsing in `RetryPolicy.fromMap(...)`
  - retry dispatch + capped exponential backoff in `WorkflowStepExecutionService`
  - transient mapping in parity FUB wait/check steps
- Phase 2 (trigger routing foundation):
  - new trigger SPI under `service/workflow/trigger`:
    - `WorkflowTriggerType`
    - `TriggerMatchContext`
    - `EntityRef`
  - FUB trigger plugin:
    - `FubWebhookTriggerType` (`webhook_fub`)
    - domain/action wildcard matching
    - optional JSONata filter evaluation
    - `resourceIds` extraction to lead entities
  - router infrastructure:
    - `WorkflowTriggerRouter`
    - active workflow lookup (`AutomationWorkflowRepository.findByStatus`)
    - deterministic fan-out cap with config `workflow.trigger-router.max-fanout-per-event` (default 200)
    - `WorkflowExecutionManager.plan(...)` fan-out integration
  - webhook processor integration:
    - router invocation added to `WebhookEventProcessorService.process(...)`
    - router failures isolated so legacy CALL/ASSIGNMENT flows continue
- Phase 3 (MVP step library):
  - catalog contract expanded:
    - `/admin/workflows/step-types` now includes `defaultRetryPolicy`
  - new FUB step:
    - `FubAddTagWorkflowStep` (`fub_add_tag`) with Wave 3 log-only execution via `FollowUpBossClient.addTag(...)`
    - transient vs permanent mapping through `StepExecutionResult.transientFailure(...)` and `failure(...)`
  - new outbound HTTP boundary:
    - `WorkflowHttpClient` port + `WorkflowRestHttpClientAdapter` adapter
    - timeout config added under `workflow.step-http.*`
  - new step types:
    - `HttpRequestWorkflowStep` (`http_request`)
    - `SlackNotifyWorkflowStep` (`slack_notify`)
  - integration alignment:
    - all `FollowUpBossClient` test doubles updated for `addTag(...)` contract
- Phase 4 (end-to-end proof):
  - added `WorkflowTriggerEndToEndTest` with three scenarios:
    - matching webhook creates workflow run and executes terminally
    - non-matching webhook creates no workflow run
    - transient Slack failure retries and then completes successfully
  - deterministic harness:
    - Testcontainers-backed Postgres integration setup
    - mutable fixed clock for deterministic retry due-at advancement
    - local HTTP server for Slack status sequencing (`503 -> 200`)
    - stubbed `FollowUpBossClient` retaining Wave 3 log-only add-tag behavior
  - side-by-side flow guard:
    - assignment webhook processing still creates policy execution runs while workflow routing also executes
  - router-planned run assertion:
    - `workflow_runs.webhook_event_id` remains `null`

## Tests Added/Updated
- New:
  - `FubWebhookTriggerTypeTest`
  - `WorkflowTriggerRouterTest`
  - `WorkflowTriggerRouterIntegrationTest` (Docker/Testcontainers dependent)
  - `FubAddTagWorkflowStepTest`
  - `HttpRequestWorkflowStepTest`
  - `SlackNotifyWorkflowStepTest`
  - `WorkflowTriggerEndToEndTest` (Docker/Testcontainers dependent)
- Updated:
  - `WebhookEventProcessorServiceTest` (router invocation + failure-isolation coverage)
  - `AdminWorkflowControllerTest` (`defaultRetryPolicy` + new step IDs in catalog assertion)
  - `FubFollowUpBossClientTest` (log-only add-tag simulation coverage)

## Validation Executed
- `./mvnw test -Dtest='AdminWorkflowControllerTest,WorkflowGraphValidatorTest'`
- `./mvnw test -Dtest='FubAddTagWorkflowStepTest,FubFollowUpBossClientTest,WorkflowParityTest'`
- `./mvnw test -Dtest='HttpRequestWorkflowStepTest,SlackNotifyWorkflowStepTest,WorkflowRetryDispatchTest'`
- `./mvnw test -Dtest='AdminWorkflowControllerTest,FubAddTagWorkflowStepTest,HttpRequestWorkflowStepTest,SlackNotifyWorkflowStepTest,WebhookEventProcessorServiceTest,WorkflowTriggerRouterTest,WorkflowTriggerRouterIntegrationTest,WorkflowEngineSmokeTest,WorkflowParityTest,WorkflowGraphValidatorTest'`
- `./mvnw test -Dtest='WorkflowGraphValidatorTest,RetryPolicyTest'`
- `./mvnw test -Dtest='FubWebhookTriggerTypeTest,ExpressionEvaluatorTest'`
- `./mvnw test -Dtest='WorkflowTriggerRouterIntegrationTest,WorkflowEngineSmokeTest'`
- `./mvnw test -Dtest='WebhookEventProcessorServiceTest,WorkflowTriggerRouterIntegrationTest,FubWebhookTriggerTypeTest'`
- `./mvnw test -Dtest='FubWebhookTriggerTypeTest,WorkflowTriggerRouterIntegrationTest,WebhookEventProcessorServiceTest,WorkflowRetryDispatchTest,WorkflowParityTest,WorkflowEngineSmokeTest,WorkflowGraphValidatorTest'`
- `./mvnw test -Dtest='WorkflowTriggerEndToEndTest#matchingWebhook_createsRun_executesTerminally'`
- `./mvnw test -Dtest='WorkflowTriggerEndToEndTest#nonMatchingWebhook_createsNoWorkflowRun'`
- `./mvnw test -Dtest='WorkflowTriggerEndToEndTest#notificationTransientFailure_thenRetrySuccess_completesRun'`
- `./mvnw test -Dtest='WorkflowTriggerRouterTest,WebhookEventProcessorServiceTest'`
- `./mvnw test -Dtest='WorkflowRetryDispatchTest,SlackNotifyWorkflowStepTest,HttpRequestWorkflowStepTest'`
- `./mvnw test -Dtest='WorkflowTriggerEndToEndTest,WorkflowTriggerRouterIntegrationTest,WorkflowEngineSmokeTest,WorkflowParityTest,WebhookEventProcessorServiceTest,WorkflowRetryDispatchTest'`
- `./mvnw test`

Validation outcome:
- All executed suites passed.
- Docker/Testcontainers-dependent suites were skipped in this environment when Docker was unavailable.
- Full-suite snapshot after Phase 4: `Tests run: 353, Failures: 0, Errors: 0, Skipped: 28`.
- Environment-gated skipped classes:
  - `AdminProcessedCallsPostgresRegressionTest`
  - `AdminWebhooksPostgresRegressionTest`
  - `AutomationPolicyMigrationPostgresRegressionTest`
  - `PolicyExecutionStepClaimRepositoryPostgresTest`
  - `WorkflowEngineSmokeTest`
  - `WorkflowParityTest`
  - `WorkflowTriggerRouterIntegrationTest`
  - `WorkflowTriggerEndToEndTest`

## Known Limitations
- `webhookEventId` in workflow plan requests remains `null` (no normalized event DB-id propagation yet).
- Trigger authoring via admin workflow API is not added in this phase (trigger JSON seeded via persistence/tests).
- Fan-out overflow handling is deterministic truncation at `workflow.trigger-router.max-fanout-per-event`.
- `fub_add_tag` is intentionally log-only in Wave 3 (no outbound FUB write).
- `http_request`/`slack_notify` do not yet enforce SSRF/egress allowlisting in Wave 3.
