# Automation Engine - Operator UI Plan

## Context

All workflow engine waves (1-4) are complete. The backend exposes 28 REST endpoints across 6 domains. The existing UI covers Webhooks, Processed Calls, and Policies. **The entire Workflow domain (17 endpoints) has zero UI coverage.** This plan adds full workflow management + runs observability, and upgrades the dashboard for operator daily use.

The user is an admin/operator who needs full operational control and observability -- no visual workflow builder, just consume all endpoints via clean admin UI.

---

## What's Being Built

### New UI Modules
1. **Workflows** -- CRUD, lifecycle management, version history, validation, step/trigger catalog reference
2. **Workflow Runs** -- Cross-workflow run list, per-run detail with step timeline, cancel controls
3. **Dashboard** -- Upgraded landing for operators with cross-domain summary

### Infrastructure Work
- `DELETE` method support in `HttpJsonClient`
- Page-based pagination component (workflows use `page/size/total`, not cursors)
- Shared `JsonViewer` for trigger/graph/payload/outputs display
- New ports, adapters, Zod schemas, query hooks

---

## Routes & Navigation

### New Routes
| Route | Page | Purpose |
|-------|------|---------|
| `/admin-ui` (index) | DashboardPage | Operator home -- replaces redirect to webhooks |
| `/admin-ui/workflows` | WorkflowsPage | List + filter + create workflows |
| `/admin-ui/workflows/:key` | WorkflowDetailPage | Definition, versions, scoped runs, lifecycle actions |
| `/admin-ui/workflow-runs` | WorkflowRunsPage | Global cross-workflow runs list |
| `/admin-ui/workflow-runs/:runId` | WorkflowRunDetailPage | Step timeline, cancel, debug detail |

### Navigation Updates
Add to `AppNavKey`: `'workflows'` | `'workflowRuns'`

```
Nav order: WH | PC | PO | WF | WR
```

**Files**: [routes.ts](ui/src/shared/constants/routes.ts), [router.tsx](ui/src/app/router.tsx)

---

## Page Designs

### Page 1: Workflows List (`/admin-ui/workflows`)

**Endpoints**: `GET /admin/workflows?status=&page=&size=`, `GET /admin/workflows/step-types`, `GET /admin/workflows/trigger-types`

**Layout**: PageHeader + FilterBar + DataTable + PagePagination

**Table columns**: Key | Name | Status | Version | Created

**Filters**: Status dropdown (DRAFT / ACTIVE / INACTIVE / ARCHIVED / All)

**Actions**:
- "Create Workflow" button -- opens modal with fields: key, name, description, trigger (JSON textarea), graph (JSON textarea), status
- Row click -- navigates to `/admin-ui/workflows/:key`

**Panel (left sidebar)**: Summary stats (total, active count, draft count)

**Inspector (right sidebar)**: Quick preview of selected row; collapsible Step Types catalog + Trigger Types catalog (fetched once, cached with `staleTime: Infinity`)

---

### Page 2: Workflow Detail (`/admin-ui/workflows/:key`)

**Endpoints**: `GET /:key`, `PUT /:key`, `GET /:key/versions`, `POST /:key/activate`, `POST /:key/deactivate`, `POST /:key/rollback`, `DELETE /:key`, `POST /validate`, `GET /:key/runs`

**Layout**: PageHeader + Tabs (Definition | Runs)

**Definition Tab**:
- Metadata rows: Key, Name, Description, Status (badge), Version
- Trigger section: JsonViewer (read-only)
- Graph section: JsonViewer (read-only)
- Action buttons:
  - **Edit** (modal with name, description, graph JSON) -- available when DRAFT/INACTIVE
  - **Validate** -- calls validate endpoint, shows inline success/error list
  - **Activate** -- ConfirmDialog
  - **Deactivate** -- ConfirmDialog (when ACTIVE)
  - **Archive** -- ConfirmDialog destructive variant
  - **Rollback** -- ConfirmDialog with version number input (from version history)

**Runs Tab**:
- Same DataTable as global runs page but filtered to this workflow key
- Columns: Run ID | Status | Reason Code | Started | Completed
- Row click navigates to `/admin-ui/workflow-runs/:runId`

**Inspector**: Version history list (versionNumber, status, createdAt) with rollback buttons

---

### Page 3: Workflow Runs Global (`/admin-ui/workflow-runs`)

**Endpoints**: `GET /admin/workflow-runs?status=&page=&size=`

**Layout**: PageHeader + FilterBar + DataTable + PagePagination

**Table columns**: Run ID | Workflow Key | Version | Status | Reason Code | Started | Completed

**Filters**: Status dropdown (PENDING / BLOCKED / DUPLICATE_IGNORED / CANCELED / COMPLETED / FAILED / All)

**Row click**: navigates to `/admin-ui/workflow-runs/:runId`

**Panel**: Summary (count shown, failed highlighted)

---

### Page 4: Workflow Run Detail (`/admin-ui/workflow-runs/:runId`)

**Endpoints**: `GET /admin/workflow-runs/:runId`, `POST /admin/workflow-runs/:runId/cancel`

**Layout**: Back link + PageHeader + Metadata + TriggerPayload + StepTimeline

**Metadata section**: Run ID, Workflow Key (link to detail), Version, Status badge, Reason Code, Source Lead ID, Event ID, Started At, Completed At

**Trigger Payload**: JsonViewer

**Step Timeline** (modeled after existing policy `StepTimeline`):
- Each step: nodeId, stepType (human name), status badge, resultCode, retryCount, dueAt
- Expandable: outputs (JsonViewer), errorMessage, dependsOnNodeIds

**Cancel button**: Shown when status is PENDING or BLOCKED. ConfirmDialog.

---

### Page 5: Dashboard (`/admin-ui` index)

**Endpoints**: Reuses existing list endpoints with `size=1` for counts, `size=5` for recent items

**Layout**: 2x2 card grid

| Card | Content |
|------|---------|
| Active Workflows | Count + link to filtered list |
| Recent Runs | Last 5 runs mini-table + link to runs page |
| Failed Runs | Count + last 5 failures + link to filtered runs |
| System Health | Active policies count, recent webhook count |

---

## Implementation Phases

### Phase 1: Foundation (adapter layer + shared components)

1. Add `DELETE` method to `HttpJsonClient` -- add `delete<T>()` method, extend `request()` method union type
2. Create `workflowSchemas.ts` -- all Zod schemas matching backend DTOs:
   - `workflowStatusSchema`, `workflowRunStatusSchema`, `workflowRunStepStatusSchema`
   - `workflowResponseSchema`, `pageResponseSchema(itemSchema)`, `validateWorkflowResponseSchema`
   - `stepTypeCatalogEntrySchema`, `triggerTypeCatalogEntrySchema`
   - `workflowVersionSummarySchema`, `workflowRunSummarySchema`, `workflowRunStepDetailSchema`, `workflowRunDetailResponseSchema`
3. Create ports: `workflowPort.ts`, `workflowRunPort.ts`
4. Create adapters: `httpWorkflowAdapter.ts`, `httpWorkflowRunAdapter.ts`
5. Register in `container.ts`, update `AppPorts`
6. Add query keys to `queryKeys.ts`
7. Create shared `PagePagination` component (page/size/total based)
8. Create shared `JsonViewer` component (read-only `<pre>`, copy button, scroll)

**Files to modify**: [httpJsonClient.ts](ui/src/platform/adapters/http/httpJsonClient.ts), [container.ts](ui/src/platform/container.ts)

**New files**:
- `ui/src/modules/workflows/lib/workflowSchemas.ts`
- `ui/src/platform/ports/workflowPort.ts`
- `ui/src/platform/ports/workflowRunPort.ts`
- `ui/src/platform/adapters/http/httpWorkflowAdapter.ts`
- `ui/src/platform/adapters/http/httpWorkflowRunAdapter.ts`
- `ui/src/shared/ui/PagePagination.tsx`
- `ui/src/shared/ui/JsonViewer.tsx`

#### Phase 1 Step-by-Step Execution Plan (Additive)

1. Read and lock contracts
   - Re-read this document's Phase 1 section.
   - Confirm backend DTO response shapes before writing Zod schemas.
   - Produce a short checklist of exact schema types to implement.

2. Add `DELETE` support in HTTP client
   - Update `ui/src/platform/adapters/http/httpJsonClient.ts`:
     - extend request method union to include `DELETE`
     - add `delete<T>()` helper
   - Keep behavior aligned with existing `get/post/put` error handling.

3. Create workflow schemas as source of truth
   - Add `ui/src/modules/workflows/lib/workflowSchemas.ts` with:
     - status enums
     - workflow/workflow-run/step DTO schemas
     - generic page response schema factory
     - validation response schema
     - step/trigger catalog schemas
   - Export inferred TypeScript types from schemas.

4. Define ports
   - Add `ui/src/platform/ports/workflowPort.ts`.
   - Add `ui/src/platform/ports/workflowRunPort.ts`.
   - Keep methods strictly aligned to existing backend endpoints.

5. Implement HTTP adapters
   - Add `ui/src/platform/adapters/http/httpWorkflowAdapter.ts`.
   - Add `ui/src/platform/adapters/http/httpWorkflowRunAdapter.ts`.
   - Parse every adapter response with Zod at the boundary.

6. Wire dependency container and query keys
   - Update `ui/src/platform/container.ts`:
     - register workflow/workflow-run adapters
     - expose them through `AppPorts`
   - Update `ui/src/platform/query/queryKeys.ts` with workflow and workflow-run keys.

7. Build shared UI primitives
   - Add `ui/src/shared/ui/PagePagination.tsx` for `page/size/total`.
   - Add `ui/src/shared/ui/JsonViewer.tsx` (read-only, copy, scroll).
   - Keep both primitives generic for reuse in Phases 2 and 3.

8. Testing gate (mandatory)
   - Add at least one new test for Phase 1 behavior (recommended set):
     - `HttpJsonClient` `delete<T>()` test
     - adapter parse test (valid + invalid payload)
     - one shared UI primitive test (`PagePagination` or `JsonViewer`)
   - Execute:
     - new tests
     - existing frontend suite
   - Do not close Phase 1 unless validation threshold is met.

9. Documentation updates (required workflow)
   - Immediately update workflow feature docs after implementation:
     - `Docs/features/workflow-engine/phases.md`
     - active phase implementation log under `Docs/features/workflow-engine/...`
   - Keep updates concise and chronological for handoff.

10. Phase 1 completion criteria
   - `DELETE` support is implemented and verified.
   - Phase 1 schemas, ports, and adapters are wired and compiling.
   - Shared pagination and JSON viewer are available.
   - New tests are added and executed; existing suite executed.
   - Feature docs reflect status and validation notes.

#### Phase 1 Implementation Status
- Status: `COMPLETED` (2026-04-16)
- Implementation notes: [phase-1-implementation.md](phase-1-implementation.md)

### Phase 2: Workflow Management Module

1. Create `workflowsDisplay.ts` -- status tone/label mappers (same pattern as `policiesDisplay.ts`)
2. Create `workflowsSearchParams.ts` -- URL state management
3. Create data hooks: `useWorkflowsQuery`, `useWorkflowDetailQuery`, `useWorkflowVersionsQuery`, `useStepTypesQuery`, `useTriggerTypesQuery`, `useCreateWorkflowMutation`, `useUpdateWorkflowMutation`, `useActivateWorkflowMutation`, `useDeactivateWorkflowMutation`, `useRollbackWorkflowMutation`, `useArchiveWorkflowMutation`, `useValidateWorkflowMutation`
4. Build `WorkflowsPage` (list, filter, pagination, create modal)
5. Build `WorkflowDetailPage` (tabs, metadata, JSON viewers, actions, version history)
6. Add routes to `router.tsx`, nav items to `routes.ts`, text to `uiText.ts`

**New files** (in `ui/src/modules/workflows/`):
- `lib/workflowsDisplay.ts`
- `lib/workflowsSearchParams.ts`
- `data/useWorkflowsQuery.ts` + 11 more hook files
- `ui/WorkflowsPage.tsx`
- `ui/WorkflowDetailPage.tsx`
- `ui/WorkflowCreateModal.tsx`
- `ui/WorkflowEditModal.tsx`
- `ui/WorkflowActions.tsx`
- `ui/WorkflowVersionList.tsx`

#### Phase 2 Step-by-Step Execution Plan (Additive)

1. Lock Phase 2 contracts and integration shape
   - Re-read the Phase 2 section in this document and align implementation scope.
   - Confirm endpoint payload/response contracts used by workflow list/detail/lifecycle APIs.
   - Keep module structure aligned to existing UI boundaries (`modules/*`, `platform/*`, `shared/*`).

2. Build workflow display and URL-state utilities
   - Implement `workflowsDisplay.ts` for status labels/tones and lifecycle action state mapping.
   - Implement `workflowsSearchParams.ts` for serializable URL state:
     - list state (`status`, `page`, `size`, selected row key)
     - detail state (`tab`, run filters/paging where applicable)
   - Keep draft filter state local and apply to URL only on explicit Apply.

3. Implement workflow data hooks (queries + mutations)
   - Add query hooks for list/detail/versions/step-types/trigger-types.
   - Add mutation hooks for create/update/activate/deactivate/rollback/archive/validate.
   - Enforce query key consistency and invalidation strategy through `queryKeys.ts`.
   - Keep catalogs (`step-types`, `trigger-types`) effectively static with long-lived caching.

4. Build `WorkflowsPage` (list + create flow)
   - Compose `PageHeader + FilterBar + DataTable + PagePagination`.
   - Include status filter, summary panel metrics, row selection/preview behavior.
   - Add create modal with `key`, `name`, `description`, `trigger`, `graph`, `status`.
   - Route row click to workflow detail path by key.

5. Build `WorkflowDetailPage` (definition + runs tabs)
   - Definition tab:
     - metadata rows (key/name/description/status/version)
     - read-only trigger/graph display using `JsonViewer`
     - lifecycle actions (edit/validate/activate/deactivate/archive/rollback) with confirmations
   - Runs tab:
     - scoped run list for the workflow key
     - row click navigation to run detail route
   - Add version-history inspector with rollback entry actions.

6. Implement modal and action components
   - Create and wire `WorkflowCreateModal`, `WorkflowEditModal`, `WorkflowActions`, `WorkflowVersionList`.
   - Enforce client-side JSON validation before create/update/validate submissions.
   - Keep action availability rules deterministic by workflow status and version preconditions.

7. Integrate routes, navigation, and text constants
   - Register `/admin-ui/workflows` and `/admin-ui/workflows/:key` in router.
   - Add workflows nav exposure in rail/panel route constants.
   - Keep policies route/nav removal intact while adding workflow navigation.
   - Add all new user-facing strings to `uiText.ts` (no scattered literals).

8. Panel/inspector composition and shell registration
   - Register route-level panel and inspector content via existing shell region pattern.
   - Ensure empty/loading/error states follow shared primitives.
   - Keep desktop/mobile shell behavior consistent with current pages.

9. Testing and validation gate (mandatory)
   - Add new tests for:
     - URL search-param parsing/creation for workflows
     - key query/mutation hooks (including invalidation behavior)
     - `WorkflowsPage` filter/pagination/create interactions
     - `WorkflowDetailPage` action visibility and validate result rendering
     - route/nav exposure for workflows and no regression on policies cutover expectations
   - Execute:
     - newly added tests
     - full frontend suite (`npm run test`)
     - lint/build checks (`npm run lint`, `npm run build`)

10. Documentation and completion criteria
   - Update workflow feature phase tracking docs after implementation/test completion:
     - `Docs/features/workflow-engine/phases.md`
     - active phase implementation notes under `Docs/features/workflow-engine/...`
   - Phase 2 is complete when:
     - workflows list and detail routes are operational
     - lifecycle and validation actions are wired with feedback paths
     - tests are added/executed and validation gates pass
     - docs are updated for handoff continuity

#### Phase 2.1 Implementation Status
- Status: `COMPLETED` (2026-04-16)
- Scope delivered in Pass 2.1:
  - `workflowsDisplay.ts` + `workflowsSearchParams.ts`
  - read-only query hooks (`useWorkflowsQuery`, `useWorkflowDetailQuery`, `useWorkflowVersionsQuery`, `useStepTypesQuery`, `useTriggerTypesQuery`)
  - route/nav exposure for `/admin-ui/workflows` and `/admin-ui/workflows/:key`
  - lightweight `WorkflowsPage` + `WorkflowDetailPage` placeholders (read-only)
  - workflow centralized text constants
- Implementation notes: [phase-2-implementation.md](phase-2-implementation.md)
- Deferred to later Phase 2 passes:
  - create/edit/lifecycle mutation UI and related action components

#### Phase 2.2 Implementation Status
- Status: `COMPLETED` (2026-04-16)
- Scope delivered in Pass 2.2:
  - `useCreateWorkflowMutation` hook with workflow list/detail invalidation
  - `WorkflowCreateModal` with field-level and JSON validation
  - `WorkflowsPage` create button + modal integration + success/error feedback
  - centralized create-flow strings in `uiText`
- Implementation notes: [phase-2-implementation.md](phase-2-implementation.md)
- Deferred to later Phase 2 passes:
  - detail-tab lifecycle actions (`update`, `activate`, `deactivate`, `rollback`, `archive`, `validate`)

#### Phase 2.3 Implementation Status
- Status: `COMPLETED` (2026-04-16)
- Scope delivered in Pass 2.3:
  - lifecycle mutation hooks (`update`, `validate`, `activate`, `deactivate`, `rollback`, `archive`)
  - `WorkflowEditModal`, `WorkflowActions`, `WorkflowVersionList`
  - `WorkflowDetailPage` lifecycle action strip + confirmations + inline validation output
  - rollback control from version-history inspector
  - centralized lifecycle action labels/messages in `uiText`
- Implementation notes: [phase-2-implementation.md](phase-2-implementation.md)
- Deferred to later phases:
  - scoped runs tab and run-surface integration (Phase 3 scope)

#### Phase 2 Closure Status
- Phase 2 is `COMPLETED` (Passes 2.1, 2.2, and 2.3 delivered and validated).

### Phase 3: Workflow Runs Module

1. Create `workflowRunsDisplay.ts` -- run + step status tone/label mappers
2. Create `workflowRunsSearchParams.ts`
3. Create data hooks: `useWorkflowRunsQuery`, `useWorkflowRunsForKeyQuery`, `useWorkflowRunDetailQuery`, `useCancelWorkflowRunMutation`
4. Build `WorkflowRunsPage` (global list with filter)
5. Build `WorkflowRunDetailPage` with `WorkflowStepTimeline`
6. Wire up Runs tab inside `WorkflowDetailPage`
7. Add routes and nav items

**New files** (in `ui/src/modules/workflow-runs/`):
- `lib/workflowRunsDisplay.ts`
- `lib/workflowRunsSearchParams.ts`
- `data/useWorkflowRunsQuery.ts` + 3 more hook files
- `ui/WorkflowRunsPage.tsx`
- `ui/WorkflowRunDetailPage.tsx`
- `ui/WorkflowStepTimeline.tsx`

#### Phase 3 Step-by-Step Execution Plan (Additive)

1. Lock run-domain contracts and phase boundaries
   - Re-read Phase 3 scope in this document and align implementation boundaries.
   - Confirm run list/detail/cancel endpoint contracts and current status enums.
   - Keep this phase focused on observability + cancel control (no retry/replay).

2. Implement run display and URL-state helpers
   - Add `workflowRunsDisplay.ts` for run/step status labels, tones, and reason-code formatting.
   - Add `workflowRunsSearchParams.ts` for serializable URL state:
     - global runs: `status`, `page`, `size`, selected run id
     - scoped runs (workflow detail tab): status/page/size/tab selection
   - Keep filter draft behavior consistent with existing pages (Apply/Reset).

3. Implement workflow-run data hooks
   - Add:
     - `useWorkflowRunsQuery`
     - `useWorkflowRunsForKeyQuery`
     - `useWorkflowRunDetailQuery`
     - `useCancelWorkflowRunMutation`
   - Wire query keys for global/scoped/detail runs and enforce deterministic invalidation on cancel.

4. Build `WorkflowRunsPage` (global list)
   - Compose `PageHeader + FilterBar + DataTable + PagePagination`.
   - Include columns: run id, workflow key, version, status, reason code, started, completed.
   - Add summary panel with shown count and failed highlight.
   - Route row click to `/admin-ui/workflow-runs/:runId`.

5. Build `WorkflowRunDetailPage` and `WorkflowStepTimeline`
   - Render metadata section, trigger payload (`JsonViewer`), and step timeline.
   - Timeline entries include node id, step type, status, result code, retry count, due at.
   - Expand/collapse step detail content: outputs JSON, error message, dependency nodes.
   - Add cancel control gated to `PENDING`/`BLOCKED` with `ConfirmDialog`.

6. Wire Runs tab integration in workflow detail
   - Replace placeholder/temporary runs-tab behavior with scoped run query hook.
   - Keep list behavior consistent with global runs page.
   - Route run selection to run detail page.

7. Integrate routes, nav, and centralized text
   - Register:
     - `/admin-ui/workflow-runs`
     - `/admin-ui/workflow-runs/:runId`
   - Add workflow-runs nav exposure in rail and panel navigation.
   - Add all new user-facing labels/messages under centralized `uiText.ts`.

8. Shell panel/inspector registration
   - Register panel and inspector content using existing shell region contract.
   - Ensure loading/error/empty fallbacks use shared primitives for consistent UX.

9. Testing and validation gate (mandatory)
   - Add tests for:
     - run/step display mapping helpers
     - workflow-runs search param parsing/serialization
     - hooks query-key/invalidation behavior
     - global list filter/pagination and row navigation
     - run detail rendering + cancel visibility/confirm flow
     - route/nav exposure and regression safety
   - Execute:
     - newly added tests
     - full frontend suite (`npm run test`)
     - lint/build checks (`npm run lint`, `npm run build`)

10. Documentation and completion criteria
   - Update feature tracking docs after implementation and validation:
     - `Docs/features/workflow-engine/phases.md`
     - active phase implementation notes under `Docs/features/workflow-engine/...`
   - Phase 3 is complete when:
     - global and scoped run lists are operational
   - run detail timeline and cancel behavior are wired
   - tests and validation gates pass
   - docs are updated for handoff continuity

#### Phase 3.1 Implementation Status
- Status: `COMPLETED` (2026-04-16)
- Scope delivered in Pass 3.1:
  - `workflowRunsDisplay.ts` + `workflowRunsSearchParams.ts`
  - read-only hooks: `useWorkflowRunsQuery`, `useWorkflowRunDetailQuery`
  - route/nav exposure for `/admin-ui/workflow-runs` and `/admin-ui/workflow-runs/:runId`
  - read-only `WorkflowRunsPage` + `WorkflowRunDetailPage`
  - centralized workflow-runs text constants in `uiText`
- Implementation notes: [phase-3-implementation.md](phase-3-implementation.md)
- Deferred to Pass 3.2:
  - scoped runs tab in `WorkflowDetailPage`
  - run cancel action and mutation wiring
  - full `WorkflowStepTimeline` component

#### Phase 3.2a Implementation Status
- Status: `COMPLETED` (2026-04-16)
- Scope delivered in Pass 3.2a:
  - `useWorkflowRunsForKeyQuery` hook
  - URL-backed runs-tab state in `workflowsSearchParams` (`tab`, `runStatus`, `runPage`, `runSize`)
  - `WorkflowDetailPage` runs tab (read-only scoped list/filter/pagination/navigation)
- Implementation notes: [phase-3-implementation.md](phase-3-implementation.md)
- Deferred to Pass 3.2b:
  - run cancel mutation + confirm flow
  - `WorkflowStepTimeline` extraction in run detail
  - Phase 3 closure status update

#### Phase 3.2b Implementation Status
- Status: `COMPLETED` (2026-04-16)
- Scope delivered in Pass 3.2b:
  - `useCancelWorkflowRunMutation` hook + invalidation helper for run detail/global/scoped run lists
  - cancel action + `ConfirmDialog` in `WorkflowRunDetailPage` (gated to `PENDING` / `BLOCKED`)
  - success/error notification wiring for cancel outcomes
  - `WorkflowStepTimeline` component extraction and integration in run detail page
  - workflow-runs centralized text updates for cancel/timeline copy
- Implementation notes: [phase-3-implementation.md](phase-3-implementation.md)

#### Phase 3 Closure Status
- Phase 3 is `COMPLETED` (Passes 3.1, 3.2a, and 3.2b delivered and validated).

### Phase 4: Dashboard

1. Create `DashboardPage` (or rework existing landing module)
2. Replace `<Navigate to="webhooks" replace />` index route with `<DashboardPage />`
3. Summary cards fetching from existing endpoints

**New file**: `ui/src/modules/dashboard/ui/DashboardPage.tsx`

#### Phase 4 Step-by-Step Execution Plan (Additive)

1. Lock dashboard contract and scope
   - Re-read Phase 4 scope in this document and keep this phase informational-only.
   - Reuse existing backend endpoints only; no new API contracts in this phase.
   - Keep dashboard actions to deep links (no destructive controls).

2. Implement `DashboardPage` structure
   - Build `/admin-ui` dashboard as a 2x2 operator card grid:
     - Active Workflows
     - Recent Runs
     - Failed Runs
     - System Health
   - Use shared layout primitives and consistent card hierarchy.

3. Implement dashboard data strategy (existing endpoints only)
   - Use list endpoints with `size=1` for count extraction and `size=5` for recent item tables.
   - Add dashboard-focused data hooks/selectors to aggregate cards and normalize loading/error states.
   - Keep server state in TanStack Query and avoid duplicated local caches.

4. Wire route index behavior
   - Replace `/admin-ui` index redirect with `<DashboardPage />`.
   - Preserve all existing feature routes unchanged.

5. Implement card content and deep links
   - Active Workflows card: count + link to filtered workflows view.
   - Recent Runs card: latest 5 runs + link to workflow-runs list.
   - Failed Runs card: failed count + latest 5 failures + link with failed status filter.
   - System Health card: active policies count + recent webhook count.
   - Keep target-page filter state URL-driven.

6. Integrate shell panel/inspector and centralized text
   - Register dashboard panel/inspector content through existing shell-region pattern.
   - Add all new dashboard labels/messages in `uiText.ts`; do not scatter string literals.
   - Use shared loading/error/empty primitives for each card state.

7. Testing and validation gate (mandatory)
   - Add tests for:
     - `/admin-ui` route now rendering dashboard
     - each card’s loading/error/empty/success rendering paths
     - “recent” cards limiting to 5 rows
     - dashboard deep links resolving to correct routes/filter states
     - hook mapping behavior for `size=1` and `size=5` query shapes
   - Execute:
     - newly added tests
     - full frontend suite (`npm run test`)
     - lint/build checks (`npm run lint`, `npm run build`)

8. Documentation and completion criteria
   - Update feature tracking docs after implementation and validation:
     - `Docs/features/workflow-engine/phases.md`
     - active phase implementation notes under `Docs/features/workflow-engine/...`
   - Phase 4 is complete when:
     - `/admin-ui` loads dashboard by default
     - all four cards render with expected data and links
     - tests and validation gates pass
     - docs are updated for handoff continuity

### Phase 5: Polish & Cross-linking

1. Cross-links: workflow key in run detail -> workflow detail; run ID in workflow detail -> run detail
2. Inspector panels for both pages
3. Step/trigger catalog reference panel on workflows page
4. Validate button integration
5. URL state management for all filter/selection state

#### Phase 5 Step-by-Step Execution Plan (Additive)

1. Lock final-phase polish scope
   - Re-read Phase 5 goals in this document and keep this phase focused on UX hardening.
   - Avoid introducing new backend contracts; reuse established APIs and hooks.
   - Treat this phase as interaction consistency + navigation reliability pass.

2. Complete bidirectional cross-linking
   - Ensure workflow key in run detail links to workflow detail route.
   - Ensure run id entries in workflow detail (Runs tab) link to run detail route.
   - Keep links keyboard-accessible and visually consistent with existing table/detail styles.

3. Harden inspector behavior across workflow surfaces
   - Normalize inspector content for:
     - workflows list
     - workflow detail
     - workflow runs list
     - workflow run detail
   - Use shell-region registration consistently and eliminate stale inspector content on selection/route changes.
   - Standardize empty/loading/error inspector states via shared primitives and centralized text.

4. Polish step/trigger catalog reference UX
   - Ensure workflows page exposes step/trigger catalogs in a usable, collapsible reference panel.
   - Keep stable ordering and readable formatting for quick operator scanning.
   - Preserve long-lived cache behavior so panel interactions do not cause unnecessary refetches.

5. Finalize validate-action UX integration
   - Ensure validate action has consistent pending, success, and failure surfaces.
   - Keep result rendering explicit and operator-friendly (clear inline outcomes).
   - Reset/retain validation output only on intentional state transitions (workflow switch, explicit dismiss/reset, or new request).

6. Complete URL-state persistence for all workflow views
   - Ensure filter, pagination, tab, and selection state are URL-backed where applicable.
   - Verify browser back/forward preserves prior workflow/runs context.
   - Ensure deep links restore expected page state without manual reconfiguration.

7. Navigation consistency and route-state continuity pass
   - Confirm rail/panel nav and in-page links maintain expected admin context.
   - Eliminate regressions where route transitions unexpectedly clear state.
   - Keep workflows and workflow-runs flows coherent end-to-end.

8. Testing and validation gate (mandatory)
   - Add tests for:
     - cross-link navigation in both directions
     - inspector state transitions (empty/loading/error/success)
     - URL-state parse/serialize + refresh/back-forward restoration
     - validate action pending/result rendering and reset semantics
     - navigation continuity across workflows <-> workflow-runs paths
   - Execute:
     - newly added tests
     - full frontend suite (`npm run test`)
     - lint/build checks (`npm run lint`, `npm run build`)

9. Documentation and completion criteria
   - Update feature tracking docs after implementation and validation:
     - `Docs/features/workflow-engine/phases.md`
     - active phase implementation notes under `Docs/features/workflow-engine/...`
   - Phase 5 is complete when:
     - cross-links and inspector behavior are consistent across workflow/runs pages
     - URL-state persistence works for key filter/tab/selection paths
     - validate UX is integrated and stable
     - tests and validation gates pass
     - docs are updated for handoff continuity

---

## Backend Gaps to Discuss

These are capabilities an operator would want but the backend doesn't currently support:

| # | Gap | Impact | Recommendation |
|---|-----|--------|----------------|
| **G1** | **No manual trigger/test run** | Can't test DRAFT workflows before activating | Add `POST /admin/workflows/:key/trigger` with test payload |
| **G2** | **No run stats/counts endpoint** | Dashboard must fetch multiple pages just for counts | Add `GET /admin/workflow-runs/stats` returning counts by status |
| **G3** | **No date range filter on runs** | Can't scope to incident window | Add `from`/`to` params to run list endpoints |
| **G4** | **No text search on workflows** | Finding workflows by name/key in large lists | Add `key`/`name` query params to list endpoint |
| **G5** | **No retry/replay for failed runs** | Unlike processed calls, can't retry failed workflow runs | Add `POST /admin/workflow-runs/:runId/retry` |
| **G6** | **No trigger config in create/update** | `CreateWorkflowRequest` has no `trigger` field; trigger is separate from graph (confirmed). Response returns it but create/update can't set it | Add `trigger` to `CreateWorkflowRequest` and `UpdateWorkflowRequest` DTOs |
| **G7** | **No source lead / event ID filter on runs** | Can't search runs by lead | Add `sourceLeadId`/`eventId` to run summary + as filter params |
| **G8** | **HttpJsonClient lacks DELETE** | Needed for archive workflow | Small fix -- add to Phase 1 |

**Priority**: G1, G3, G5, G6 are high-impact for operator workflows. G2, G4 are nice-to-have. G8 is required.

**Decision**: Build UI first consuming what exists today. All gaps will be addressed in a follow-up iteration. For G6 (trigger in create/update), the UI will display trigger as read-only in detail view but the create/update modals will include a trigger JSON textarea -- it just won't work until the backend DTOs are updated. We'll note this clearly in the UI.

---

## Verification Plan

1. **Phase 1**: Manually call each adapter method against running backend, verify Zod parsing succeeds
2. **Phase 2**: Create a workflow via UI, verify it appears in list; test activate/deactivate/archive lifecycle; validate with intentional errors
3. **Phase 3**: Trigger a workflow run (via webhook or manual), verify it appears in both global and per-workflow run lists; inspect step detail; test cancel on a pending run
4. **Phase 4**: Load dashboard, verify all cards show correct counts and link to correct filtered views
5. **Phase 5**: Click through all cross-links; verify URL state persists across navigation; test filter reset
6. **E2E**: Full flow -- create workflow -> activate -> trigger run -> observe in runs -> inspect steps -> cancel/complete
