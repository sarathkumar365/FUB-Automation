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

### Phase 4: Dashboard

1. Create `DashboardPage` (or rework existing landing module)
2. Replace `<Navigate to="webhooks" replace />` index route with `<DashboardPage />`
3. Summary cards fetching from existing endpoints

**New file**: `ui/src/modules/dashboard/ui/DashboardPage.tsx`

### Phase 5: Polish & Cross-linking

1. Cross-links: workflow key in run detail -> workflow detail; run ID in workflow detail -> run detail
2. Inspector panels for both pages
3. Step/trigger catalog reference panel on workflows page
4. Validate button integration
5. URL state management for all filter/selection state

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