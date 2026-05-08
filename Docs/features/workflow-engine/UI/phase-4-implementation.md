# Workflow Operator UI — Phase 4 Implementation

## Date
- 2026-04-16

## Pass 4.1 Scope Delivered
Dashboard data foundation is implemented (no route cutover/page wiring yet):
- Added dashboard snapshot mapper/types:
  - `ui/src/modules/dashboard/lib/dashboardSnapshot.ts`
- Added dashboard query hook:
  - `ui/src/modules/dashboard/data/useDashboardSnapshotQuery.ts`
- Added dedicated query key namespace:
  - `queryKeys.dashboard.snapshot()`
- Added centralized dashboard copy constants:
  - `uiText.dashboard.*`

## Behavior Delivered in Pass 4.1
- Snapshot composes existing endpoints only:
  - active workflows count via `listWorkflows({ status: 'ACTIVE', page: 0, size: 1 })`
  - recent runs via `listWorkflowRuns({ page: 0, size: 5 })`
  - failed runs count/list via `listWorkflowRuns({ status: 'FAILED', page: 0, size: 5 })`
  - recent webhooks via `listWebhooks({ limit: 5 })`
- System Health card data contract is intentionally placeholder-first:
  - `mode: "placeholder"`
  - includes recent webhook summary values for operator visibility
  - no speculative “agent-to-lead accountability” metric math in this pass

## Explicitly Deferred (Pass 4.2+ Scope)
- No `/admin-ui` index route cutover to dashboard yet.
- No `DashboardPage` UI card layout integration yet.
- No dashboard panel/inspector route registration yet.

## Tests Added
- `ui/src/test/dashboard-utils.test.ts`
- `ui/src/test/dashboard-hooks.test.tsx`

## Validation Results (Pass 4.1)
- `npm run test`
  - Result: pass (39 files, 136 tests)
- `npm run lint`
  - Result: pass
- `npm run build`
  - Result: pass

## Notes For Pass 4.2
- Build `DashboardPage` card UI against `useDashboardSnapshotQuery`.
- Wire `/admin-ui` index route to dashboard page.
- Add dashboard deep-link rendering and route-level tests.

## Pass 4.2 Scope Delivered
Dashboard route/UI integration is implemented on top of Pass 4.1 data foundation:
- Added dashboard page composition:
  - `ui/src/modules/dashboard/ui/DashboardPage.tsx`
- Replaced `/admin-ui` index redirect with dashboard page route:
  - `ui/src/app/router.tsx`
- Extended dashboard centralized copy for card/table/shell region labels:
  - `ui/src/shared/constants/uiText.ts`

## Behavior Delivered in Pass 4.2
- `/admin-ui` now renders dashboard by default (no redirect to webhooks).
- Dashboard cards consume `useDashboardSnapshotQuery` snapshot contract:
  - Active Workflows count + deep link (`/admin-ui/workflows?status=ACTIVE`)
  - Recent Runs mini-table + deep link (`/admin-ui/workflow-runs`)
  - Failed Runs count/mini-table + deep link (`/admin-ui/workflow-runs?status=FAILED`)
  - System Health placeholder card using pass-through webhook summary fields
- Dashboard registers shell panel + inspector route-region content.

## Tests Added/Updated (Pass 4.2)
- Added:
  - `ui/src/test/dashboard-page.test.tsx`
    - success rendering
    - loading/error/empty states
    - recent list cap assertions (5)
    - dashboard deep-link route assertions
- Updated:
  - `ui/src/test/app-routing.test.tsx`
    - `/admin-ui` now asserts dashboard index rendering

## Validation Results (Pass 4.2)
- Targeted route/dashboard regression:
  - `npm run test -- dashboard-page.test.tsx app-routing.test.tsx`
  - Result: pass
- Full frontend suite:
  - `npm run test`
  - Result: pass (40 files, 140 tests)
- Lint/build:
  - `npm run lint` — pass
  - `npm run build` — pass
