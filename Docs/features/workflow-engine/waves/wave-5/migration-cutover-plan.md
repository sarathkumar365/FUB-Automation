# Workflow Engine Migration Cutover Plan (Wave 5)

## Summary

This document captures the minimum practical migration plan to decommission the legacy policy path and cut over to workflow-only runtime.

- Plan size: `N=9` steps
- Mode: workflow-only cutover
- API posture: hard-remove policy admin APIs
- UI posture: disconnect policy route/nav (hide access; keep module files for now)
- Data posture: drop legacy policy tables now

## 9-Step Plan

1. Freeze cutover contract.
   - Confirm migration intent in docs: workflow-only execution, hard-remove policy APIs, hide policy UI route/nav, drop policy tables now.

2. Cut webhook assignment tether to policy.
   - Remove policy planning from assignment event processing.
   - Keep workflow trigger routing as the only assignment automation path.

3. Hard-remove policy admin endpoints.
   - Remove `/admin/policies` and `/admin/policy-executions` controller exposure and related API contracts from active backend surface.

4. Disable legacy policy runtime entrypoints.
   - Ensure policy worker/runtime path cannot execute in app runtime.
   - Keep legacy code files present but disconnected.

5. Disconnect policy UI access.
   - Remove policies route and nav items from router/constants.
   - Ensure no startup/navigation dependency still touches policy ports.

6. Drop policy tables via Flyway.
   - Add migration to drop `policy_execution_steps`, `policy_execution_runs`, `automation_policies` in dependency-safe order.

7. Realign tests to workflow-only behavior.
   - Remove/replace tests that require policy endpoints/tables/runtime.
   - Update webhook tests to assert no policy planning path.

8. Run full validation suites.
   - Execute backend and frontend suites and confirm required success threshold.
   - Fix regressions introduced by endpoint/table removal.

9. Update docs for handoff.
   - Update workflow feature phase docs and status trackers with migration step details.
   - Record decommission decisions and source-of-truth behavior.

## Assumptions

- Historical policy data is intentionally removed by dropping legacy policy tables.
- Full legacy policy module deletion is deferred to a follow-up phase.
- Workflow builder UI and retry controls remain deferred and out of this cutover.
