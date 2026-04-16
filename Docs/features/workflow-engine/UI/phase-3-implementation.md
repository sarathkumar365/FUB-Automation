# Workflow Operator UI — Phase 3 Implementation

## Date
- 2026-04-16

## Pass 3.1 Scope Delivered
Read-only workflow-runs global surfaces are implemented:
- Added workflow-runs display helpers:
  - `ui/src/modules/workflow-runs/lib/workflowRunsDisplay.ts`
- Added workflow-runs URL-state helpers:
  - `ui/src/modules/workflow-runs/lib/workflowRunsSearchParams.ts`
- Added read-only workflow-runs query hooks:
  - `ui/src/modules/workflow-runs/data/useWorkflowRunsQuery.ts`
  - `ui/src/modules/workflow-runs/data/useWorkflowRunDetailQuery.ts`
- Added workflow-runs pages:
  - `ui/src/modules/workflow-runs/ui/WorkflowRunsPage.tsx`
  - `ui/src/modules/workflow-runs/ui/WorkflowRunDetailPage.tsx`
- Added route/nav exposure:
  - routes: `/admin-ui/workflow-runs`, `/admin-ui/workflow-runs/:runId`
  - nav key: `workflowRuns`
- Added centralized text entries for workflow-runs pages in `uiText`.

## Explicitly Deferred (per Pass 3.1 boundary)
- No scoped runs tab integration inside `WorkflowDetailPage` (Pass 3.2 scope).
- No cancel mutation controls in run detail (Pass 3.2 scope).
- No full step timeline component (`WorkflowStepTimeline`) in this pass.
- No `useWorkflowRunsForKeyQuery` or `useCancelWorkflowRunMutation` in this pass.

## Tests Added
- `ui/src/test/workflow-runs-utils.test.ts`
- `ui/src/test/workflow-runs-hooks.test.tsx`
- `ui/src/test/workflow-runs-page.test.tsx`
- `ui/src/test/workflow-run-detail-page.test.tsx`
- Updated route/nav regression:
  - `ui/src/test/policy-cutover-routing.test.ts`

## Validation Results
- `npm run test`
  - Result: pass (36 files, 120 tests)
- `npm run lint`
  - Result: pass
- `npm run build`
  - Result: pass

## Notes For Pass 3.2
- Add scoped workflow runs tab to `WorkflowDetailPage` using `listWorkflowRunsForKey`.
- Add run cancel mutation flow and confirm dialog in run detail page.
- Replace lightweight step list with dedicated timeline component.

## Pass 3.2a Scope Delivered
Scoped workflow-runs tab integration is implemented (read-only):
- Added scoped run query hook:
  - `ui/src/modules/workflow-runs/data/useWorkflowRunsForKeyQuery.ts`
- Extended workflow detail URL-state helpers:
  - detail tab now supports `definition | runs`
  - runs tab URL state supports `runStatus`, `runPage`, `runSize`
- Updated `WorkflowDetailPage`:
  - added Definition/Runs tab controls (URL-backed)
  - added scoped runs table with status filter + Apply/Reset and pagination
  - row navigation to global run detail route
- Added centralized `uiText` entries required by runs tab labels/table/filter copy.

## Explicitly Deferred (still Pass 3.2b scope)
- No cancel action/mutation in run detail page.
- No `WorkflowStepTimeline` extraction/refactor yet.
- No Phase 3 closure status update yet.

## Additional Tests Added For Pass 3.2a
- Updated: `ui/src/test/workflow-runs-hooks.test.tsx` (scoped query hook coverage)
- Updated: `ui/src/test/workflows-utils.test.ts` (detail runs-tab URL state coverage)
- Updated: `ui/src/test/workflow-detail-page-actions.test.tsx` (scoped runs tab integration behavior)

## Pass 3.2b Scope Delivered
Run-cancel controls and timeline extraction are implemented:
- Added cancel mutation hook + invalidation helper:
  - `ui/src/modules/workflow-runs/data/useCancelWorkflowRunMutation.ts`
  - `ui/src/modules/workflow-runs/data/workflowRunMutationInvalidation.ts`
- Updated run display helpers:
  - `canCancelWorkflowRun` added in `ui/src/modules/workflow-runs/lib/workflowRunsDisplay.ts`
- Extracted timeline component:
  - `ui/src/modules/workflow-runs/ui/WorkflowStepTimeline.tsx`
- Updated run detail page:
  - cancel action visibility gated to `PENDING` and `BLOCKED`
  - cancel `ConfirmDialog` + success/error notifications
  - timeline component now renders run steps (replacing inline step list)
- Updated centralized text constants:
  - cancel and timeline labels/messages under `uiText.workflowRuns`

## Additional Tests Added/Updated For Pass 3.2b
- Updated: `ui/src/test/workflow-runs-hooks.test.tsx`
  - cancel mutation payload + query invalidation coverage
- Updated: `ui/src/test/workflow-runs-utils.test.ts`
  - cancel visibility gating helper coverage
- Updated: `ui/src/test/workflow-run-detail-page.test.tsx`
  - cancel visibility by status
  - confirm flow + mutation invocation
  - success/error notification behavior
  - query invalidation assertions
- Added: `ui/src/test/workflow-step-timeline.test.tsx`
  - timeline rendering + details section coverage

## Validation Results (Pass 3.2b)
- `npm run test`
  - Result: pass (37 files, 133 tests)
- `npm run lint`
  - Result: pass
- `npm run build`
  - Result: pass

## Phase 3 Status
- Phase 3 is `COMPLETED` (Pass 3.1 + Pass 3.2a + Pass 3.2b delivered and validated on 2026-04-16).
