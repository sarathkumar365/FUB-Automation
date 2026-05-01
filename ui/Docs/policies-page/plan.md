# Policies Page — Implementation Plan

Date: 2026-04-09

## Visual Style Reference

All UI follows the established visual language from `Docs/archive/ui-style-guide-v1.md` and `ui/src/styles/tokens.css`:

- **Tone:** Internal operations console — clean, high-clarity, professional
- **Surfaces:** Light UI (#f7f9fc bg, #ffffff cards), subtle borders (#dde6f2), low-elevation shadow
- **Brand:** Cyan (#0f9fb8) + Teal (#0d9488) primary palette
- **Status colors:** Teal for success, amber for warning, rose for error — always color + label text (never color-only)
- **Typography:** Manrope for UI text, JetBrains Mono for IDs / timestamps / technical values
- **Spacing:** 4px grid via CSS custom properties (--space-1 through --space-5)
- **Radius:** 8px (sm), 12px (md), pill (badges)
- **Density:** Low-clutter spacing, clear grouping. No dense telemetry blocks.
- **Filters:** Structured controls with explicit Apply / Reset. No freeform search.
- **Components:** Reuse shared primitives (DataTable, StatusBadge, FilterBar, PageHeader, etc.) from `shared/ui/`
- **Accessibility:** Keyboard-navigable rows and controls. Status communicated via color + text.

---

## Product Decision: One Unified Page

Policy Management and Policy Executions merge into a single **"Policies"** page at `/admin-ui/policies`.

**Why merge:** A manager's mental model is "my policies and how they're doing" — not two separate concepts. Policies are few (2-5), executions are many. Splitting into two pages forces unnecessary navigation for a closely related workflow.

**The page has two tabs in the content area:**
- **Runs** (default) — execution monitoring, the primary view
- **Manage** — policy CRUD, the secondary view

---

## User Profile

The user is a manager / super-user who wants medium-to-maximum visibility into what's happening in the system. They want to:
- See all SLA runs at a glance — what's pending, what completed, what failed
- Drill into a specific run to see the step-by-step timeline
- Occasionally create or tweak a policy without leaving the page
- Understand trends without needing to query an API

---

## Page Layout

### Runs Tab (Default View)

```
+-- Rail --+-- Panel (260px) -----+-- Content -----------------------------------+-- Inspector (320px) -----------+
|          |                      |                                               |                               |
| ...      | FILTERS              |  Runs                     Manage              |  Run #42                      |
| Policies | --------             |  ~~~~~~                                       |                               |
| ...      | Policy:  [All     v] |  +------------------------------------------+ |  Status: COMPLETED            |
|          | Status:  [All     v] |  | ID  | Lead  | Policy  | Status | When     | |  Outcome: COMPLIANT_CLOSED    |
|          | From:    [________]  |  | 42  | 456   | SLA v3  | COMP   | 2m ago   | |  Lead: 456                    |
|          | To:      [________]  |  | 41  | 789   | SLA v3  | PEND   | 5m ago   | |  Policy: FOLLOW_UP_SLA v3     |
|          |                      |  | 40  | 123   | SLA v2  | FAIL   | 12m ago  | |  Source: FUB                  |
|          | [Apply]  [Reset]     |  | 39  | 321   | SLA v3  | COMP   | 18m ago  | |  Event: evt_abc123            |
|          |                      |  +------------------------------------------+ |                               |
|          |                      |                                               |  --- Step Timeline ----------  |
|          | SUMMARY              |  <- prev   next ->                            |                               |
|          | --------             |                                               |  [done] 1. Check Claim        |
|          | Active policies: 2   |                                               |     Result: CLAIMED            |
|          | Runs today: 14       |                                               |     Due: 10:05 AM             |
|          | Failed: 1            |                                               |     Completed: 10:04 AM       |
|          |                      |                                               |          |                    |
|          |                      |                                               |  [done] 2. Check Comms        |
|          |                      |                                               |     Result: COMM_FOUND         |
|          |                      |                                               |     Due: 10:15 AM             |
|          |                      |                                               |     Completed: 10:14 AM       |
|          |                      |                                               |          |                    |
|          |                      |                                               |  [skip] 3. Execute Action     |
|          |                      |                                               |     Result: SKIPPED            |
|          |                      |                                               |     (lead was compliant)       |
+----------+----------------------+-----------------------------------------------+-------------------------------+
```

### Manage Tab

```
+-- Rail --+-- Panel (260px) -----+-- Content -----------------------------------+-- Inspector (320px) -----------+
|          |                      |                                               |                               |
| ...      | FILTERS              |  Runs                     Manage              |  Policy: FOLLOW_UP_SLA        |
| Policies | --------             |                            ~~~~~~             |                               |
| ...      | Domain:  [All     v] |  +------------------------------------------+ |  Domain: ASSIGNMENT           |
|          |                      |  | Key          | v | Status | Enabled       | |  Status: ACTIVE               |
|          |                      |  | FOLLOW_UP_SLA| 3 | ACTIVE | Yes           | |  Version: 3                   |
|          |                      |  | FOLLOW_UP_SLA| 2 | INACT  | Yes           | |  Enabled: Yes                 |
|          |                      |  | FOLLOW_UP_SLA| 1 | INACT  | No            | |                               |
|          |                      |  +------------------------------------------+ |  --- Blueprint --------       |
|          |                      |                                               |                               |
|          |                      |  [+ New Policy]                               |  Step 1: Check Claim          |
|          |                      |                                               |    Wait: 5 minutes            |
|          |                      |                                               |          |                    |
|          |                      |                                               |  Step 2: Check Comms          |
|          |                      |                                               |    Wait: 10 minutes           |
|          |                      |                                               |          |                    |
|          |                      |                                               |  Step 3: Execute Action       |
|          |                      |                                               |    Action: REASSIGN            |
|          |                      |                                               |    Target: User 12345         |
|          |                      |                                               |                               |
|          |                      |                                               |  [Edit]  [Activate]           |
+----------+----------------------+-----------------------------------------------+-------------------------------+
```

### Edit Policy (Modal)

When "Edit" or "+ New Policy" is clicked, a modal opens:

```
+-----------------------------------------------------------+
|  Edit Policy — FOLLOW_UP_SLA v3                           |
|                                                           |
|  Enabled:  [x] Yes                                        |
|                                                           |
|  --- Steps ---                                            |
|                                                           |
|  1. Check Claim                                           |
|     Wait: [ 5  ] minutes                                  |
|                                                           |
|  2. Check Communication                                   |
|     Wait: [ 10 ] minutes                                  |
|                                                           |
|  3. Execute Action (if SLA breached)                      |
|     Action: [ REASSIGN    v ]                             |
|     Target User ID: [ 12345   ]                           |
|                                                           |
|                          [Cancel]  [Save as v4]           |
+-----------------------------------------------------------+
```

For "+ New Policy":

```
+-----------------------------------------------------------+
|  New Policy                                               |
|                                                           |
|  Domain:     [ ASSIGNMENT     ]                           |
|  Policy Key: [ FOLLOW_UP_SLA  ]                           |
|  Enabled:    [x] Yes                                      |
|                                                           |
|  Template:   [ ASSIGNMENT_FOLLOWUP_SLA_V1  v ]            |
|                                                           |
|  --- Steps ---                                            |
|  (same step editor as edit modal)                         |
|                                                           |
|                          [Cancel]  [Create]               |
+-----------------------------------------------------------+
```

---

## What Each Element Displays

### Runs Table Columns

| Column | Source Field | Display |
|--------|------------|---------|
| ID | `id` | Run ID number |
| Lead | `sourceLeadId` | Lead ID or "-" if null |
| Policy | `policyKey` + `policyVersion` | e.g. "SLA v3" (truncate long keys) |
| Status | `status` | StatusBadge with tone mapping |
| When | `createdAt` | Relative time ("2m ago", "1h ago") |

### Run Status Badge Tones

| Status | Tone | Label |
|--------|------|-------|
| PENDING | info | Pending |
| BLOCKED_POLICY | warning | Blocked |
| DUPLICATE_IGNORED | neutral | Duplicate |
| COMPLETED | success | Completed |
| FAILED | error | Failed |

### Step Timeline (Inspector)

Each step shows:
- **Icon**: done/pending/processing/failed/skipped based on `status`
- **Label**: Human-readable step type name
- **Result**: `resultCode` in plain text, or status if no result yet
- **Due at**: `dueAt` formatted as time
- **Completed at**: `updatedAt` if status is terminal
- **Error**: `errorMessage` shown in red if present
- **Connector line** between steps showing dependency chain

### Step Status Icons

| Status | Icon | Color |
|--------|------|-------|
| PENDING | clock | gray |
| WAITING_DEPENDENCY | hourglass | gray |
| PROCESSING | spinner | blue |
| COMPLETED | checkmark | green |
| FAILED | x-circle | red |
| SKIPPED | skip-forward | gray |

### Policy Table Columns (Manage Tab)

| Column | Source Field | Display |
|--------|------------|---------|
| Key | `policyKey` | Policy key name |
| v | `version` | Version number |
| Status | `status` | StatusBadge (ACTIVE=green, INACTIVE=gray) |
| Enabled | `enabled` | Yes/No |

### Policy Inspector Blueprint

Parse the `blueprint` JSON and render as a readable step list:
- For each step in `steps[]`: show type (human name), delay in minutes
- Show `actionConfig.actionType` and target for the final step
- No raw JSON shown — always the parsed visual representation

### Step Type Human Names

| StepType | Display Name |
|----------|-------------|
| WAIT_AND_CHECK_CLAIM | Check Claim |
| WAIT_AND_CHECK_COMMUNICATION | Check Communication |
| ON_FAILURE_EXECUTE_ACTION | Execute Action |

### Action Type Labels

| ActionType | Display |
|-----------|---------|
| REASSIGN | Reassign to User |
| MOVE_TO_POND | Move to Pond |

### Panel Summary (Runs tab)

Three stats computed client-side from the current page data + a lightweight count:
- **Active policies**: count from policy list query (filtered to ACTIVE)
- **Runs today**: count label (can use the table data or a dedicated query later)
- **Failed**: count of FAILED in current view

---

## Data Flow

### API Endpoints Consumed

| Endpoint | Used In | Purpose |
|----------|---------|---------|
| `GET /admin/policy-executions` | Runs tab | Paginated, filtered execution list |
| `GET /admin/policy-executions/{id}` | Inspector (run) | Run detail with steps |
| `GET /admin/policies` | Manage tab, Policy filter dropdown | Policy list |
| `GET /admin/policies/{domain}/{policyKey}/active` | Inspector (policy) | Active policy detail |
| `POST /admin/policies` | Create modal | Create new policy |
| `PUT /admin/policies/{id}` | Edit modal | Update policy |
| `POST /admin/policies/{id}/activate` | Inspector action | Activate a policy version |

### No New Backend Endpoints Needed

Everything above exists. Zero backend work.

---

## File Structure

Following existing module patterns:

```
ui/src/
  modules/
    policies/
      ui/
        PoliciesPage.tsx              -- Page component with tab switching
        RunsTab.tsx                   -- Runs table + pagination
        ManageTab.tsx                 -- Policy versions table
        RunInspector.tsx              -- Run detail + step timeline
        PolicyInspector.tsx           -- Policy detail + blueprint view
        StepTimeline.tsx              -- Vertical step timeline component
        BlueprintView.tsx             -- Parsed blueprint display (reused in inspector + modal)
        PolicyFormModal.tsx           -- Create/Edit policy modal
        policiesDisplay.ts            -- Status tones, step type labels, formatting helpers
      data/
        usePolicyExecutionsQuery.ts   -- List query with filters + cursor pagination
        usePolicyExecutionDetailQuery.ts -- Detail query by ID
        usePoliciesQuery.ts           -- Policy list query
        useActivePolicyQuery.ts       -- Active policy detail query
        useCreatePolicyMutation.ts    -- Create mutation
        useUpdatePolicyMutation.ts    -- Update mutation
        useActivatePolicyMutation.ts  -- Activate mutation
      lib/
        policiesSearchParams.ts       -- URL state: tab, filters, selected run/policy
        policySchemas.ts              -- Zod schemas for all policy API responses
  platform/
    ports/
      policyPort.ts                   -- Port interface for policy CRUD
      policyExecutionPort.ts          -- Port interface for execution queries
    adapters/
      http/
        httpPolicyAdapter.ts          -- HTTP adapter for policy port
        httpPolicyExecutionAdapter.ts -- HTTP adapter for execution port
  shared/
    constants/
      routes.ts                       -- Add policies nav item
      queryKeys.ts                    -- Add policy + execution query keys
      uiText.ts                       -- Add policies section text
```

---

## Implementation Steps

### Step 1: Platform Layer (ports, adapters, schemas)

1. **`policySchemas.ts`** — Zod schemas matching backend DTOs:
   - `policyResponseSchema` — id, domain, policyKey, enabled, blueprint, status, version
   - `policyExecutionRunListItemSchema` — id, source, eventId, sourceLeadId, domain, policyKey, policyVersion, status, reasonCode, createdAt, updatedAt
   - `policyExecutionRunPageSchema` — items[], nextCursor, serverTime
   - `policyExecutionRunDetailSchema` — all list fields + webhookEventId, policyBlueprintSnapshot, idempotencyKey, steps[]
   - `policyExecutionStepSchema` — id, stepOrder, stepType, status, dueAt, dependsOnStepOrder, resultCode, errorMessage, createdAt, updatedAt

2. **`policyPort.ts`** — Interface:
   ```
   listPolicies(filters: {domain?, policyKey?}): Promise<PolicyResponse[]>
   getActivePolicy(domain, policyKey): Promise<PolicyResponse>
   createPolicy(cmd): Promise<PolicyResponse>
   updatePolicy(id, cmd): Promise<PolicyResponse>
   activatePolicy(id, cmd): Promise<PolicyResponse>
   ```

3. **`policyExecutionPort.ts`** — Interface:
   ```
   listExecutions(query): Promise<PolicyExecutionRunPage>
   getExecutionDetail(id): Promise<PolicyExecutionRunDetail>
   ```

4. **`httpPolicyAdapter.ts`** + **`httpPolicyExecutionAdapter.ts`** — HTTP implementations using `HttpJsonClient`

5. **Register in `container.ts`** and **`AppPorts`** type

6. **Add query keys** to `queryKeys.ts`:
   ```
   policies: { list: (filters) => [...], active: (domain, key) => [...] }
   policyExecutions: { list: (filters) => [...], detail: (id) => [...] }
   ```

7. **Add nav item** to `routes.ts`:
   ```
   { key: 'policies', to: '/admin-ui/policies', railLabel: 'PO' }
   ```

### Step 2: Data Layer (query hooks + mutations)

1. **`usePolicyExecutionsQuery.ts`** — Wraps `policyExecutionPort.listExecutions` with TanStack Query. Accepts filter params (status, policyKey, from, to, limit, cursor). Enabled flag for tab switching.

2. **`usePolicyExecutionDetailQuery.ts`** — Wraps `getExecutionDetail(id)`. Enabled when a run is selected.

3. **`usePoliciesQuery.ts`** — Wraps `policyPort.listPolicies`. Used in Manage tab and filter dropdown.

4. **`useCreatePolicyMutation.ts`** — Wraps `createPolicy`. Invalidates policy list on success.

5. **`useUpdatePolicyMutation.ts`** — Wraps `updatePolicy`. Invalidates policy list on success.

6. **`useActivatePolicyMutation.ts`** — Wraps `activatePolicy`. Invalidates policy list on success.

### Step 3: URL State Management

**`policiesSearchParams.ts`** — Manages all page state via URL search params:

```
Parameters:
  tab: "runs" | "manage"         (default: "runs")
  status: PolicyExecutionRunStatus | "ALL"
  policyKey: string | "ALL"
  from: ISO date string
  to: ISO date string
  cursor: string                 (pagination cursor)
  selectedRun: number            (selected run ID for inspector)
  selectedPolicy: number         (selected policy ID for inspector)
```

Tab switching clears the selection from the other tab. Selecting a run clears selectedPolicy and vice versa.

### Step 4: Display Helpers

**`policiesDisplay.ts`**:
- `runStatusTone(status)` — maps PolicyExecutionRunStatus to StatusBadge tone
- `runStatusLabel(status)` — maps to display label
- `stepStatusIcon(status)` — maps PolicyExecutionStepStatus to icon name
- `stepTypeLabel(type)` — maps PolicyStepType to human name
- `policyStatusTone(status)` — maps PolicyStatus to badge tone
- `actionTypeLabel(type)` — maps action type to display text
- `formatRelativeTime(dateStr)` — "2m ago", "1h ago" etc.

### Step 5: UI Components

Build in this order (each is independently testable):

1. **`StepTimeline.tsx`** — Vertical timeline of steps. Receives `PolicyExecutionStepResponse[]`. Each step: icon + label + result + timestamps. Connector lines between steps. This is the key UX piece.

2. **`BlueprintView.tsx`** — Parsed blueprint display. Receives `blueprint: Map<string, any>`. Shows steps as a readable list with delay minutes and action config. Reused in PolicyInspector and PolicyFormModal.

3. **`RunInspector.tsx`** — Run detail view for the inspector region. Shows: status badge, outcome, lead ID, policy info, source, event ID. Below that: StepTimeline component.

4. **`PolicyInspector.tsx`** — Policy detail view. Shows: domain, key, status, version, enabled. Below that: BlueprintView. Action buttons: Edit (opens modal), Activate (with confirm dialog).

5. **`RunsTab.tsx`** — DataTable of execution runs. Columns: ID, Lead, Policy, Status, When. Row click sets selectedRun in URL. Pagination with prev/next using cursor. Uses existing DataTable component.

6. **`ManageTab.tsx`** — DataTable of policy versions. Columns: Key, Version, Status, Enabled. Row click sets selectedPolicy. "New Policy" button.

7. **`PolicyFormModal.tsx`** — Modal for create/edit. Fields: domain + policyKey (create only), enabled toggle, step delay inputs, action type dropdown, target ID input. Save triggers create or update mutation. On update, bumps version automatically.

8. **`PoliciesPage.tsx`** — Orchestrator. Manages tab state, registers shell regions (panel + inspector), renders active tab. Panel shows filters (Runs tab) or domain filter (Manage tab) + summary stats. Inspector shows RunInspector or PolicyInspector based on selection.

### Step 6: Route Registration

Add to `router.tsx`:
```tsx
{ path: 'policies', element: <PoliciesPage /> }
```

### Step 7: Add uiText entries

Add `policies` section to `uiText.ts` with all labels, titles, empty states, button text.

---

## Behavior Details

### Tab Switching
- Tabs render at the top of the Content area as simple text buttons with underline active state
- Switching tabs preserves filter state but clears selection (selectedRun/selectedPolicy)
- Default tab is "Runs" — the monitoring view that a manager opens first

### Run Selection
- Click a row in the Runs table → `selectedRun` set in URL → detail query fires → RunInspector renders
- Click same row again → deselects (inspector clears)
- Inspector shows loading state while detail query fetches

### Policy Selection
- Click a row in Manage table → `selectedPolicy` set → PolicyInspector renders
- Shows full blueprint in readable format

### Activate Policy
- Button in PolicyInspector, only shown if policy status is INACTIVE
- Click → ConfirmDialog → mutation with `expectedVersion` from current policy
- On success: invalidate policy list, show success notification
- On stale version error: show "Policy was modified by someone else, please refresh"

### Edit Policy
- Button in PolicyInspector → opens PolicyFormModal pre-filled with current blueprint
- Domain and policyKey are read-only (can't change identity)
- Save sends UpdatePolicyRequest with `expectedVersion`
- On success: invalidate policy list, close modal, show notification

### Create Policy
- "+ New Policy" button above Manage table → opens PolicyFormModal empty
- Domain and policyKey are editable text fields
- Template is pre-selected to ASSIGNMENT_FOLLOWUP_SLA_V1 (only option)
- Steps pre-populated with template defaults (claim 5min, comms 10min, action REASSIGN)
- Save sends CreatePolicyRequest

### Pagination (Runs)
- Cursor-based: "Next" button sends cursor from response
- "Prev" not natively supported by cursor pagination — track cursor stack in component state
- Show "Next" only when `nextCursor` is non-null
- Default limit: 25 items per page (matching existing pattern)

### Filters (Runs)
- Draft state pattern: user changes filter inputs, clicks "Apply" to update URL params
- "Reset" clears all filters back to defaults (ALL/ALL/empty/empty)
- Filter changes reset cursor to null (back to first page)
- Policy dropdown populated from policies query (show all unique policyKeys + "All")

### Empty States
- No runs: "No execution runs found. Runs appear here when leads trigger active policies."
- No policies: "No policies created yet. Create your first policy to start automating."
- No selection (inspector): Don't register inspector region (hides the panel)

### Error States
- Query errors: Show ErrorState component in the respective area
- Mutation errors: Show error notification via useNotify()
- Stale version on mutation: specific message about concurrent edit

---

## Order of Work

| # | Task | Depends On |
|---|------|-----------|
| 1 | Zod schemas (`policySchemas.ts`) | — |
| 2 | Port interfaces (`policyPort.ts`, `policyExecutionPort.ts`) | #1 |
| 3 | HTTP adapters + container registration | #2 |
| 4 | Query keys + route + uiText constants | — |
| 5 | Query hooks (all 3 queries) | #3, #4 |
| 6 | Mutation hooks (create, update, activate) | #3, #4 |
| 7 | URL state management (`policiesSearchParams.ts`) | — |
| 8 | Display helpers (`policiesDisplay.ts`) | — |
| 9 | `StepTimeline.tsx` | #8 |
| 10 | `BlueprintView.tsx` | #8 |
| 11 | `RunInspector.tsx` | #5, #9 |
| 12 | `PolicyInspector.tsx` | #5, #10 |
| 13 | `RunsTab.tsx` | #5, #7, #8 |
| 14 | `ManageTab.tsx` | #5, #7, #8 |
| 15 | `PolicyFormModal.tsx` | #6, #10 |
| 16 | `PoliciesPage.tsx` (orchestrator) | #11-15 |
| 17 | Route registration in `router.tsx` | #16 |

Steps 1-4, 7-8 can all be done in parallel (no dependencies).
Steps 9-10 can be done in parallel.
Steps 11-14 can be done in parallel.
