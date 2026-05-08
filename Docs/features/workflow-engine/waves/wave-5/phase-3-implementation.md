# Wave 5 — Pass 3 Implementation (Policy Runtime Decommission)

## Status
Completed

## Date
2026-04-16

## Behavior Changes Delivered
- Legacy policy runtime tables are removed from runtime schema via Flyway `V12`.
- Policy worker no longer activates by default; it is now explicit opt-in only.
- Legacy policy-runtime tests were removed from active regression suite and replaced with workflow-cutover-safe coverage.

## Code Changes (Pass 3)
- Database migration:
  - Added `src/main/resources/db/migration/V12__drop_legacy_policy_runtime_tables.sql`.
  - Drops:
    - `policy_execution_steps`
    - `policy_execution_runs`
    - `automation_policies`
- Worker guardrail hardening:
  - `policy.worker.enabled=false` in `src/main/resources/application.properties`.
  - `PolicyExecutionDueWorker` conditional updated to `matchIfMissing=false`.
- Test suite realignment:
  - Added:
    - `src/test/java/com/fuba/automation_engine/integration/PolicyTableDropMigrationPostgresRegressionTest.java`
    - updated `src/test/java/com/fuba/automation_engine/service/policy/PolicyExecutionDueWorkerActivationTest.java`
  - Kept:
    - `src/test/java/com/fuba/automation_engine/integration/PolicyAdminApiCutoverIntegrationTest.java`
  - Removed legacy policy-runtime focused tests from active suite.

## Validation
- `./mvnw test -Dtest='PolicyTableDropMigrationPostgresRegressionTest,PolicyExecutionDueWorkerActivationTest,PolicyAdminApiCutoverIntegrationTest'`
  - Result: passed (`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`)
  - Build line: `BUILD SUCCESS`
  - Duration line: `Total time: 8.377 s`

## Handoff Notes (Pass 4)
- Pass 4 should execute full backend + frontend validation and close Wave 5 docs/status.
- Deferred scope remains unchanged: workflow retry controls and workflow builder UI.
