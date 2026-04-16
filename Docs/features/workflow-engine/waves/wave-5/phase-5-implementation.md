# Wave 5 — Pass 5 Implementation (Legacy Policy Surface Removal)

## Status
Completed

## Date
2026-04-16

## Behavior Changes Delivered
- Removed the disconnected legacy policy code surface from backend and frontend runtime.
- Preserved workflow scheduled execution by moving scheduling enablement to workflow config.
- Kept existing cutover behavior unchanged: workflow-only execution path remains active.

## Code Changes (Pass 5)
- Backend cleanup:
  - Deleted legacy policy subsystem packages:
    - `service/policy`
    - `exception/policy`
  - Deleted policy persistence artifacts:
    - policy entities/enums
    - policy repositories/claim adapters
  - Deleted policy-only DTO contracts.
  - Deleted policy worker config classes/properties and removed `policy.worker.*` keys from `application.properties`.
  - Added replacement verification test:
    - `src/test/java/com/fuba/automation_engine/integration/LegacyPolicySurfaceRemovalIntegrationTest.java`
- Scheduling preservation:
  - Moved `@EnableScheduling` to `WorkflowWorkerSchedulingConfig`.
- Frontend cleanup:
  - Deleted `ui/src/modules/policies/**`.
  - Deleted policy ports/adapters and removed policy wiring from platform container.
  - Removed orphaned policy query keys/defaults and policy text constants.

## Validation
- `./mvnw test`
  - Result: passed (`Tests run: 275, Failures: 0, Errors: 0, Skipped: 0`)
  - Build line: `BUILD SUCCESS`
  - Duration line: `Total time: 01:14 min`
- `npm run test -- policy-cutover-routing.test.ts app-routing.test.tsx` (from `ui/`)
  - Result: passed (`Test Files: 2 passed`, `Tests: 4 passed`)
- `npm run test` (from `ui/`)
  - Result: passed (`Test Files: 20 passed`, `Tests: 72 passed`)
- Reachability verification:
  - `rg` checks confirm no remaining references to removed backend policy classes/modules, removed UI policy modules/ports/adapters, or `policy.worker.*`.
