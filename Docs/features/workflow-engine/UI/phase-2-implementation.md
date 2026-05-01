# Workflow Operator UI — Phase 2 Implementation

## Date
- 2026-04-16

## Pass 2.1 Scope Delivered
Read-only workflow management foundation is implemented:
- Added workflow display helpers:
  - `ui/src/modules/workflows/lib/workflowsDisplay.ts`
- Added workflow URL-state helpers:
  - `ui/src/modules/workflows/lib/workflowsSearchParams.ts`
- Added read-only workflow query hooks:
  - `ui/src/modules/workflows/data/useWorkflowsQuery.ts`
  - `ui/src/modules/workflows/data/useWorkflowDetailQuery.ts`
  - `ui/src/modules/workflows/data/useWorkflowVersionsQuery.ts`
  - `ui/src/modules/workflows/data/useStepTypesQuery.ts`
  - `ui/src/modules/workflows/data/useTriggerTypesQuery.ts`
- Added lightweight workflow pages:
  - `ui/src/modules/workflows/ui/WorkflowsPage.tsx`
  - `ui/src/modules/workflows/ui/WorkflowDetailPage.tsx`
- Added route/nav exposure for workflows:
  - routes: `/admin-ui/workflows`, `/admin-ui/workflows/:key`
  - nav key: `workflows`
- Added centralized text entries for workflow pages in `uiText`.

## Explicitly Deferred (per Pass 2.1 boundary)
- No create/edit/lifecycle mutation UI in this pass:
  - `create`, `update`, `activate`, `deactivate`, `rollback`, `archive`, `validate` flows are deferred.
- No workflow-runs module pages in this pass.

## Tests Added
- `ui/src/test/workflows-utils.test.ts`
- `ui/src/test/workflow-hooks.test.tsx`
- Updated route/nav regression:
  - `ui/src/test/policy-cutover-routing.test.ts`

## Validation Results
- `npm run test`
  - Result: pass (25 files, 90 tests)
- `npm run lint`
  - Result: pass
- `npm run build`
  - Result: pass

## Notes For Pass 2.2
- Foundation utilities, query hooks, and route exposure are in place.
- Next pass should add create flow and richer list interactions on top of this baseline.

## Pass 2.2 Scope Delivered
Create-flow vertical slice is implemented on top of Pass 2.1:
- Added create mutation hook:
  - `ui/src/modules/workflows/data/useCreateWorkflowMutation.ts`
- Added create modal UI:
  - `ui/src/modules/workflows/ui/WorkflowCreateModal.tsx`
- Wired create flow into workflows list page:
  - create action button in page header
  - modal open/close lifecycle
  - client-side JSON validation before submit
  - success/error notifications
  - list refetch/invalidation after successful create
- Added centralized text entries for create flow states/messages in `uiText`.

## Explicitly Deferred (still out of Pass 2.2 scope)
- No edit/lifecycle mutation UI in this pass:
  - `update`, `activate`, `deactivate`, `rollback`, `archive`, `validate` remain deferred.
- No workflow-runs pages in this pass.

## Additional Tests Added For Pass 2.2
- `ui/src/test/workflow-create-modal.test.tsx`
- Updated workflow hooks coverage:
  - `ui/src/test/workflow-hooks.test.tsx` (create mutation invalidation path)

## Validation Results (Pass 2.2)
- `npm run test`
  - Result: pass (26 files, 94 tests)
- `npm run lint`
  - Result: pass
- `npm run build`
  - Result: pass

## Notes For Pass 2.3
- Create workflow behavior is now available from list page.
- Next pass should add definition-tab actions and lifecycle controls in detail view.

## Pass 2.3 Scope Delivered
Workflow detail lifecycle controls are implemented (definition-only, no runs tab):
- Added workflow mutation hooks:
  - `ui/src/modules/workflows/data/useUpdateWorkflowMutation.ts`
  - `ui/src/modules/workflows/data/useValidateWorkflowMutation.ts`
  - `ui/src/modules/workflows/data/useActivateWorkflowMutation.ts`
  - `ui/src/modules/workflows/data/useDeactivateWorkflowMutation.ts`
  - `ui/src/modules/workflows/data/useRollbackWorkflowMutation.ts`
  - `ui/src/modules/workflows/data/useArchiveWorkflowMutation.ts`
- Added shared invalidation helper:
  - `ui/src/modules/workflows/data/workflowMutationInvalidation.ts`
- Added workflow detail UI components:
  - `ui/src/modules/workflows/ui/WorkflowEditModal.tsx`
  - `ui/src/modules/workflows/ui/WorkflowActions.tsx`
  - `ui/src/modules/workflows/ui/WorkflowVersionList.tsx`
- Refactored `WorkflowDetailPage`:
  - action strip with deterministic status gating
  - edit/validate/activate/deactivate/archive controls + confirmations
  - rollback via version inspector with confirmation
  - inline validation result rendering
  - definition-only detail search state support (runs tab still deferred)
- Extended workflow display/search helpers and centralized `uiText` strings for lifecycle actions.

## Explicitly Deferred (out of Pass 2.3 scope)
- No scoped runs tab in workflow detail.
- No global workflow-runs pages (Phase 3 scope).

## Additional Tests Added For Pass 2.3
- `ui/src/test/workflow-mutation-hooks.test.tsx`
- `ui/src/test/workflow-edit-modal.test.tsx`
- `ui/src/test/workflow-actions.test.tsx`
- `ui/src/test/workflow-version-list.test.tsx`
- `ui/src/test/workflow-detail-page-actions.test.tsx`
- Updated utility coverage:
  - `ui/src/test/workflows-utils.test.ts`

## Validation Results (Pass 2.3)
- `npm run test`
  - Result: pass (31 files, 106 tests)
- `npm run lint`
  - Result: pass
- `npm run build`
  - Result: pass

## Phase 2 Closure
- Phase 2 is now complete (Pass 2.1 + Pass 2.2 + Pass 2.3 delivered).
- Remaining workflow UI work transitions to Phase 3 (workflow runs module).

## Post-Review Fixes (Phase 2.3)
- Fixed validation feedback behavior in `WorkflowDetailPage`:
  - success toast now appears only when validate response is `valid: true`
  - invalid responses now show warning feedback and keep inline error rendering
- Fixed workflows table selected-row identity mismatch in `WorkflowsPage`:
  - `DataTable` row key now uses workflow `key`, aligned with URL `selectedKey` state
- Added/updated regression tests:
  - `ui/src/test/workflow-detail-page-actions.test.tsx` (invalid validate path feedback)
  - `ui/src/test/workflows-page-selection.test.tsx` (selected row `aria-pressed` state)

## Validation Results (Post-Review Fixes)
- `npm run test`
  - Result: pass (32 files, 107 tests)
- `npm run lint`
  - Result: pass
- `npm run build`
  - Result: pass
