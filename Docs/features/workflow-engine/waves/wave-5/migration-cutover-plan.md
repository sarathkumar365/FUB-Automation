# Workflow Engine Migration Cutover Plan (Wave 5)

## Summary

This document captures the minimum practical migration plan to decommission the legacy policy path and cut over to workflow-only runtime.

- Plan size: `N=9` steps
- Mode: workflow-only cutover
- API posture: hard-remove policy admin APIs
- UI posture: disconnect policy route/nav (hide access; keep module files for now)
- Data posture: legacy policy runtime tables dropped in Pass 3 via Flyway `V12`; legacy policy code surface removed in Pass 5

## Pass Status

### Pass 1 — Contract Freeze + Assignment Runtime Cutover
- Status: `COMPLETED`
- Date: `2026-04-16`
- Scope delivered:
  - Wave 5 trackers updated to activate migration wave.
  - Assignment webhook processing cut over to workflow-routing-only behavior (policy planning removed from runtime path).
  - Unit/integration tests realigned for no side-by-side policy planning assumption.

### Pass 1 Validation Log
- `./mvnw test -Dtest='WebhookEventProcessorServiceTest,WorkflowTriggerEndToEndTest'`
  - Result: passed (`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`)
- `./mvnw test`
  - Result: passed (`Tests run: 406, Failures: 0, Errors: 0, Skipped: 0`)

### Pass 2 — Policy API/UI Disconnect
- Status: `COMPLETED`
- Date: `2026-04-16`
- Scope delivered:
  - `/admin/policies` and `/admin/policy-executions` are no longer exposed by runtime controllers.
  - Policy UI route/navigation wiring was removed from the active admin app router/nav.
  - Legacy policy module code and policy runtime tables were kept in place (no table-drop in this pass).
  - Endpoint-bound backend tests were replaced with explicit 404 cutover assertions.
  - Frontend routing/nav regression coverage was added for policy route/nav disconnect.

### Pass 2 Validation Log
- `./mvnw test -Dtest='PolicyAdminApiCutoverIntegrationTest,WebhookEventProcessorServiceTest,WorkflowTriggerEndToEndTest'`
  - Result: passed (`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`)
- `npm run test -- policy-cutover-routing.test.ts app-routing.test.tsx` (from `ui/`)
  - Result: passed (`Test Files: 2 passed`, `Tests: 4 passed`)
- `./mvnw test`
  - Result: passed (`Tests run: 383, Failures: 0, Errors: 0, Skipped: 0`)

### Pass 3 — Policy Runtime Decommission
- Status: `COMPLETED`
- Date: `2026-04-16`
- Scope delivered:
  - Added Flyway migration `V12__drop_legacy_policy_runtime_tables.sql`.
  - Dropped legacy policy runtime tables:
    - `policy_execution_steps`
    - `policy_execution_runs`
    - `automation_policies`
  - Policy worker became opt-in:
    - `policy.worker.enabled=false` default in `application.properties`
    - `PolicyExecutionDueWorker` conditional requires explicit enable (`matchIfMissing=false`)
  - Legacy policy-runtime focused tests removed and replaced with cutover-safe coverage.

### Pass 3 Validation Log
- `./mvnw test -Dtest='PolicyTableDropMigrationPostgresRegressionTest,PolicyExecutionDueWorkerActivationTest,PolicyAdminApiCutoverIntegrationTest'`
  - Result: passed (`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`)

### Pass 4 — Full Validation + Docs/Handoff
- Status: `COMPLETED`
- Date: `2026-04-16`
- Scope delivered:
  - Ran full backend validation for post-cutover runtime.
  - Ran targeted and full frontend validation for policy route/nav cutover.
  - Updated Wave 5 status/docs for handoff closure.

### Pass 4 Validation Log
- `./mvnw test` (non-escalated sandbox run)
  - Result: failed due environment restriction (`Operation not permitted` on local socket bind in test setup; `BUILD FAILURE`; `Tests run: 279, Failures: 0, Errors: 87, Skipped: 26`)
- `./mvnw test` (rerun with escalated permissions)
  - Result: passed (`Tests run: 279, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`; `Total time: 01:06 min`)
- `npm run test -- policy-cutover-routing.test.ts app-routing.test.tsx` (from `ui/`)
  - Result: passed (`Test Files: 2 passed`, `Tests: 4 passed`)
- `npm run test` (from `ui/`)
  - Result: passed (`Test Files: 20 passed`, `Tests: 72 passed`)

### Pass 5 — Legacy Policy Surface Removal
- Status: `COMPLETED`
- Date: `2026-04-16`
- Scope delivered:
  - Deleted legacy policy backend modules (`service/policy`, policy persistence artifacts, policy DTOs, policy exceptions, policy worker config/properties).
  - Preserved workflow scheduling by moving `@EnableScheduling` to workflow worker config.
  - Deleted legacy policy frontend modules (`ui/src/modules/policies/**`, policy ports/adapters, policy container wiring, policy constants/query keys).
  - Added replacement verification test to assert legacy policy beans are absent.

### Pass 5 Validation Log
- `./mvnw test` (cleanup pass run)
  - Result: passed (`Tests run: 275, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`; `Total time: 01:14 min`)
- `npm run test -- policy-cutover-routing.test.ts app-routing.test.tsx` (from `ui/`)
  - Result: passed (`Test Files: 2 passed`, `Tests: 4 passed`)
- `npm run test` (from `ui/`)
  - Result: passed (`Test Files: 20 passed`, `Tests: 72 passed`)

## 9-Step Plan

1. Freeze cutover contract.
   - Completed in Pass 1.

2. Cut webhook assignment tether to policy.
   - Completed in Pass 1.

3. Hard-remove policy admin endpoints.
   - Completed in Pass 2.

4. Disable legacy policy runtime entrypoints.
   - Completed in Pass 3.

5. Disconnect policy UI access.
   - Completed in Pass 2.

6. Drop policy tables via Flyway.
   - Completed in Pass 3 via `V12`.

7. Realign tests to workflow-only behavior.
   - Completed in Pass 3.

8. Run full validation suites.
   - Completed in Pass 4.

9. Update docs for handoff.
   - Completed in Pass 4.

## Assumptions

- Legacy policy module deletion is now complete.
- Workflow builder UI and retry controls remain deferred and out of this cutover.
