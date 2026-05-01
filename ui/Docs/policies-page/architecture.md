# Policies Page — UI Architecture & Data Flow

This document describes how the Policies page works end-to-end: shell layout, component tree, data flow, URL state, API calls, and mutation lifecycle.

---

## 1. Shell Layout

The page lives inside `AppShell`, which provides four regions:

```
[ Rail ] [ Panel ] [       Content       ] [ Inspector ]
  nav     filters    RunsTab / ManageTab     detail view
```

- **Rail** — Global nav. Clicking "Policies" navigates to `/policies`.
- **Panel** — Filter controls + summary stats. Registered via `useShellRegionRegistration`.
- **Content** — The main area. Renders `PoliciesPage` via React Router `<Outlet>`.
- **Inspector** — Context-sensitive detail pane. Shows run detail, policy detail, or an empty hint.

On mobile, Panel and Inspector collapse into overlay drawers toggled from the header.

---

## 2. Component Tree

```
PoliciesPage (orchestrator)
├── Panel body (registered to shell)
│   ├── Policy dropdown (Select)
│   ├── Status dropdown (Select)
│   ├── From / To date inputs
│   ├── Apply / Reset buttons
│   └── Summary (active policies, runs shown, failed)
│
├── Tab buttons (Runs | Manage)
│
├── RunsTab (tab === 'runs')
│   ├── DataTable (id, lead, policy, status, when)
│   └── Prev / Next pagination
│
├── ManageTab (tab === 'manage')
│   ├── "+ New Policy" button
│   └── DataTable (key, version, status, enabled)
│
├── Inspector body (registered to shell)
│   ├── RunInspector (when a run is selected)
│   │   ├── Detail fields (status, outcome, lead, policy, source, event, created)
│   │   └── StepTimeline (vertical timeline of execution steps)
│   ├── PolicyInspector (when a policy is selected)
│   │   ├── Detail fields (domain, key, status, version, enabled)
│   │   ├── BlueprintView (parsed step list with action config)
│   │   └── Edit / Activate buttons
│   └── Empty hint (when nothing selected)
│
├── PolicyFormModal (Radix Dialog — create or edit)
│   ├── Domain + Policy Key (readonly on edit)
│   ├── Enabled checkbox
│   ├── Step 1: Check Claim delay
│   ├── Step 2: Check Communication delay
│   └── Step 3: Action config (type + target ID)
│
└── ConfirmDialog (activate confirmation)
```

---

## 3. URL State

All filter/selection state lives in the URL via `useSearchParams`. This makes the page bookmarkable and shareable.

**Search params:**

| Param            | Type     | Default | Description                      |
|------------------|----------|---------|----------------------------------|
| `tab`            | string   | `runs`  | Active tab: `runs` or `manage`   |
| `status`         | string   | —       | Run status filter                |
| `policyKey`      | string   | —       | Policy key filter                |
| `from`           | string   | —       | Date range start (YYYY-MM-DD)    |
| `to`             | string   | —       | Date range end (YYYY-MM-DD)      |
| `cursor`         | string   | —       | Cursor for pagination            |
| `selectedRun`    | number   | —       | Selected run ID (inspector)      |
| `selectedPolicy` | number   | —       | Selected policy ID (inspector)   |

**Key files:**
- `policiesSearchParams.ts` — `parsePoliciesSearchParams()`, `createPoliciesSearchParams()`, `toPoliciesFilterDraft()`

### Draft Filter Pattern

Filters use a "draft" pattern (same as the Webhooks page):
1. URL holds the **committed** filter state (what the API is queried with).
2. `draftFilterState` holds what the user is currently typing/selecting.
3. Clicking **Apply** writes the draft into the URL → triggers a query.
4. Clicking **Reset** clears both draft and URL.
5. A `filterDraftKey` tracks whether the draft is stale relative to the URL.

---

## 4. Data Flow — Reads

### 4.1 Hexagonal Architecture

```
Component (React)
  → useXxxQuery (TanStack Query hook)
    → Port interface (TypeScript interface)
      → HTTP Adapter (fetch call)
        → Backend REST endpoint
```

All port interfaces live in `platform/ports/`. HTTP implementations live in `platform/adapters/http/`. They're wired together in `platform/container.ts` and injected via React Context (`PortsContext` → `useAppPorts()`).

### 4.2 Queries

| Hook                            | Port Method                   | Endpoint                                    | When                      |
|---------------------------------|-------------------------------|---------------------------------------------|---------------------------|
| `usePoliciesQuery()`            | `policyPort.listPolicies()`   | `GET /admin/policies`                       | Always (feeds filter dropdown + manage tab) |
| `usePolicyExecutionsQuery()`    | `policyExecutionPort.listExecutions()` | `GET /admin/policy-executions?...`  | Only when `tab === 'runs'` |
| `usePolicyExecutionDetailQuery()` | `policyExecutionPort.getExecutionDetail()` | `GET /admin/policy-executions/:id` | Only when `selectedRun` is set |

### 4.3 Query Key Factory

All keys are in `platform/query/queryKeys.ts`:
```
policies.list(filters)           → ['policies', 'list', { domain?, policyKey? }]
policies.active(domain, key)     → ['policies', 'active', domain, key]
policyExecutions.list(filters)   → ['policy-executions', 'list', { status?, policyKey?, from?, to?, limit, cursor? }]
policyExecutions.detail(id)      → ['policy-executions', 'detail', id]
```

### 4.4 Zod Schema Validation

Every API response is validated at the adapter boundary using Zod schemas (`policySchemas.ts`). If the backend returns an unexpected shape, parsing throws before the data reaches components.

---

## 5. Data Flow — Writes (Mutations)

### 5.1 Create Policy

```
User clicks "+ New Policy" → PolicyFormModal opens (create mode)
  → fills form → clicks "Create"
    → handleFormSubmit()
      → createMutation.mutate(data)
        → policyPort.createPolicy(cmd)
          → POST /admin/policies  { domain, policyKey, enabled, blueprint }
            → onSuccess: notify + close modal
            → onSettled: invalidate ['policies'] queries
```

### 5.2 Edit Policy (creates new version)

```
User selects a policy (Manage tab) → PolicyInspector shows → clicks "Edit"
  → PolicyFormModal opens (edit mode, domain+key readonly)
    → changes fields → clicks "Save as vN+1"
      → handleFormSubmit()
        → updateMutation.mutate({ id, cmd })
          → policyPort.updatePolicy(id, cmd)
            → PUT /admin/policies/:id  { enabled, expectedVersion, blueprint }
              → onSuccess: notify + close modal
              → onSettled: invalidate ['policies'] queries
```

### 5.3 Activate Policy

```
User selects an INACTIVE policy → PolicyInspector shows "Activate" button → clicks it
  → ConfirmDialog opens
    → clicks "Activate"
      → handleActivateConfirm()
        → activateMutation.mutate({ id, cmd })
          → policyPort.activatePolicy(id, cmd)
            → POST /admin/policies/:id/activate  { expectedVersion }
              → onSuccess: notify + close dialog
              → onSettled: invalidate ['policies'] queries
```

All mutations use optimistic version checks (`expectedVersion`) to prevent stale writes.

---

## 6. Pagination

The executions endpoint uses **cursor-based pagination** (not page numbers).

```
                                   cursorStack
                                   ┌─────────┐
Page 1 → (no cursor)               │         │
  → User clicks "Next"             │ ""      │  ← pushed
Page 2 → cursor=abc                │         │
  → User clicks "Next"             │ "", abc │  ← pushed
Page 3 → cursor=def                │         │
  → User clicks "Prev"             │ ""      │  ← popped "abc"
Page 2 → cursor=abc                │         │
```

- **Next**: push current cursor onto `cursorStack`, navigate to `nextCursor` from the response.
- **Prev**: pop from `cursorStack`, navigate to that cursor.
- On filter change: clear `cursorStack` and `cursor` (back to page 1).

---

## 7. Blueprint Handling

Policies have a `blueprint` field (JSON object) that defines the execution steps.

**Current template:** `ASSIGNMENT_FOLLOWUP_SLA_V1`

```json
{
  "templateKey": "ASSIGNMENT_FOLLOWUP_SLA_V1",
  "steps": [
    { "type": "WAIT_AND_CHECK_CLAIM", "delayMinutes": 5 },
    { "type": "WAIT_AND_CHECK_COMMUNICATION", "delayMinutes": 10, "dependsOn": "WAIT_AND_CHECK_CLAIM" },
    { "type": "ON_FAILURE_EXECUTE_ACTION", "dependsOn": "WAIT_AND_CHECK_COMMUNICATION" }
  ],
  "actionConfig": {
    "actionType": "REASSIGN",
    "targetUserId": 12345
  }
}
```

- **PolicyFormModal** — builds this JSON from individual form fields (`buildBlueprint()`). On edit, it parses the existing blueprint back into fields (`parseExistingBlueprint()`).
- **BlueprintView** — reads the JSON and renders a human-friendly step list. Falls back to raw JSON for unknown templates.

---

## 8. Display Helpers

`policiesDisplay.ts` contains all formatting/mapping functions:

| Function              | Purpose                                          |
|-----------------------|--------------------------------------------------|
| `runStatusTone()`     | Maps run status → StatusBadge tone (success/error/warning/info) |
| `runStatusLabel()`    | Maps run status → display label                  |
| `stepStatusTone()`    | Maps step status → StatusBadge tone              |
| `stepStatusLabel()`   | Maps step status → display label                 |
| `stepTypeLabel()`     | Maps step type enum → readable label             |
| `policyStatusTone()`  | Maps policy status → StatusBadge tone            |
| `policyStatusLabel()` | Maps policy status → display label               |
| `actionTypeLabel()`   | Maps action type → display label                 |
| `formatRelativeTime()`| ISO date string → "5m ago", "2h ago", "3d ago"   |
| `formatPolicyLabel()` | Policy key + version → truncated display label   |

All user-facing strings are centralized in `shared/constants/uiText.ts` under the `policies` key.

---

## 9. File Map

```
modules/policies/
├── data/
│   ├── usePoliciesQuery.ts              # List all policies
│   ├── usePolicyExecutionsQuery.ts      # List execution runs (filtered, paginated)
│   ├── usePolicyExecutionDetailQuery.ts # Single run detail
│   ├── useCreatePolicyMutation.ts       # Create policy
│   ├── useUpdatePolicyMutation.ts       # Update policy (new version)
│   └── useActivatePolicyMutation.ts     # Activate policy
├── lib/
│   ├── policySchemas.ts                 # Zod schemas (API boundary validation)
│   ├── policiesSearchParams.ts          # URL state parse/serialize/draft
│   └── policiesDisplay.ts              # Status tones, labels, formatting
└── ui/
    ├── PoliciesPage.tsx                 # Orchestrator (state, queries, mutations, shell registration)
    ├── RunsTab.tsx                      # Runs DataTable + pagination
    ├── RunInspector.tsx                 # Run detail view
    ├── StepTimeline.tsx                 # Vertical step timeline
    ├── ManageTab.tsx                    # Policy versions DataTable
    ├── PolicyInspector.tsx              # Policy detail view
    ├── BlueprintView.tsx                # Blueprint → readable steps
    └── PolicyFormModal.tsx              # Create/edit form (Radix Dialog)

platform/
├── ports/
│   ├── policyPort.ts                    # PolicyPort interface + command types
│   └── policyExecutionPort.ts           # PolicyExecutionPort interface + filter type
├── adapters/http/
│   ├── httpPolicyAdapter.ts             # HTTP impl of PolicyPort
│   └── httpPolicyExecutionAdapter.ts    # HTTP impl of PolicyExecutionPort
├── query/
│   └── queryKeys.ts                     # Query key factories
└── container.ts                         # DI wiring (ports → adapters)
```
