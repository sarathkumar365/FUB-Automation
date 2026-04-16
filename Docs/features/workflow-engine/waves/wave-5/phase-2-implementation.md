# Wave 5 — Pass 2 Implementation (Policy API/UI Disconnect)

## Status
Completed

## Date
2026-04-16

## Behavior Changes Delivered
- Runtime no longer exposes policy admin endpoints:
  - `/admin/policies`
  - `/admin/policy-executions`
- Admin UI no longer exposes policy route/navigation:
  - policies route removed from router
  - policies nav entries removed from shared route/nav constants
- Policy module code and database tables remain intact in this pass.

## Code Changes (Pass 2)
- Backend runtime:
  - Removed policy admin controller exposure by deleting:
    - `AdminPolicyController`
    - `AdminPolicyExecutionController`
- Backend tests:
  - Removed endpoint-era tests bound to legacy policy API controllers.
  - Added `PolicyAdminApiCutoverIntegrationTest` to assert legacy policy admin routes return `404`.
- Frontend runtime:
  - Removed `PoliciesPage` route registration from `ui/src/app/router.tsx`.
  - Removed policy nav keys/routes from `ui/src/shared/constants/routes.ts`.
- Frontend tests:
  - Added `ui/src/test/policy-cutover-routing.test.ts` to assert policy route/nav are not exposed.

## Validation
- `./mvnw test -Dtest='PolicyAdminApiCutoverIntegrationTest,WebhookEventProcessorServiceTest,WorkflowTriggerEndToEndTest'`
  - Result: passed (`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`)
- `npm run test -- policy-cutover-routing.test.ts app-routing.test.tsx` (from `ui/`)
  - Result: passed (`Test Files: 2 passed`, `Tests: 4 passed`)
- `./mvnw test`
  - Result: passed (`Tests run: 383, Failures: 0, Errors: 0, Skipped: 0`)

## Deferred to Later Pass
- Policy runtime table drop migration (`automation_policies`, `policy_execution_runs`, `policy_execution_steps`)
- Full legacy policy module teardown
- Workflow retry controls and workflow builder UI remain deferred as previously planned
