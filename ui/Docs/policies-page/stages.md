# Policies Page — Implementation Stages

Date: 2026-04-09
Parent: [plan.md](plan.md)

## Overview

The Policies page is built in 6 stages. Each stage produces a working, testable increment. No stage depends on skipping ahead.

```
Stage 1: Platform wiring        (ports, adapters, schemas, constants)
Stage 2: Runs tab — read-only   (list + filters + pagination)
Stage 3: Run inspector          (detail + step timeline)
Stage 4: Manage tab — read-only (policy list + policy inspector)
Stage 5: Policy mutations       (create, edit, activate)
Stage 6: Polish                 (empty/error states, responsiveness, tests)
```

---

## Stage 1 — Platform Wiring

**Goal:** All API plumbing is in place. No UI yet — just ports, adapters, schemas, query hooks, and constants. Every later stage consumes this.

**Files to create:**

| File | Purpose |
|------|---------|
| `modules/policies/lib/policySchemas.ts` | Zod schemas for all policy + execution API responses |
| `platform/ports/policyPort.ts` | Interface: listPolicies, getActivePolicy, createPolicy, updatePolicy, activatePolicy |
| `platform/ports/policyExecutionPort.ts` | Interface: listExecutions, getExecutionDetail |
| `platform/adapters/http/httpPolicyAdapter.ts` | HTTP implementation of policyPort |
| `platform/adapters/http/httpPolicyExecutionAdapter.ts` | HTTP implementation of policyExecutionPort |
| `modules/policies/data/usePoliciesQuery.ts` | TanStack Query hook for policy list |
| `modules/policies/data/usePolicyExecutionsQuery.ts` | TanStack Query hook for execution list (filtered, paginated) |
| `modules/policies/data/usePolicyExecutionDetailQuery.ts` | TanStack Query hook for execution detail |

**Files to update:**

| File | Change |
|------|--------|
| `platform/container.ts` | Register new adapters, add to AppPorts type |
| `shared/constants/queryKeys.ts` | Add policies + policyExecutions key factories |
| `shared/constants/routes.ts` | Add policies nav item `{ key: 'policies', to: '/admin-ui/policies', railLabel: 'PO' }` |
| `shared/constants/uiText.ts` | Add policies section with all labels |
| `app/router.tsx` | Add `/policies` route (can point to placeholder initially) |

**Zod schemas to define:**

```
policyResponseSchema:
  id: number, domain: string, policyKey: string, enabled: boolean,
  blueprint: record(string, unknown), status: enum(ACTIVE, INACTIVE),
  version: number

policyExecutionRunListItemSchema:
  id: number, source: enum(FUB, INTERNAL), eventId: string | null,
  sourceLeadId: string | null, domain: string, policyKey: string,
  policyVersion: number, status: enum(PENDING, BLOCKED_POLICY,
  DUPLICATE_IGNORED, COMPLETED, FAILED), reasonCode: string | null,
  createdAt: string, updatedAt: string

policyExecutionRunPageSchema:
  items: array(runListItemSchema), nextCursor: string | null,
  serverTime: string

policyExecutionStepSchema:
  id: number, stepOrder: number, stepType: enum(WAIT_AND_CHECK_CLAIM,
  WAIT_AND_CHECK_COMMUNICATION, ON_FAILURE_EXECUTE_ACTION),
  status: enum(PENDING, WAITING_DEPENDENCY, PROCESSING, COMPLETED,
  FAILED, SKIPPED), dueAt: string | null, dependsOnStepOrder: number | null,
  resultCode: string | null, errorMessage: string | null,
  createdAt: string, updatedAt: string

policyExecutionRunDetailSchema:
  ...runListItemSchema fields + webhookEventId: number | null,
  policyBlueprintSnapshot: record(string, unknown),
  idempotencyKey: string, steps: array(stepSchema)
```

**Done when:** All hooks return typed data from the running backend. You can verify with a throwaway component or console log. `npm run lint` and `npm run build` pass.

---

## Stage 2 — Runs Tab (Read-Only)

**Goal:** The Policies page renders with the Runs tab showing execution data in a table with filters and pagination. No inspector yet — clicking a row does nothing useful.

**Files to create:**

| File | Purpose |
|------|---------|
| `modules/policies/lib/policiesSearchParams.ts` | URL state: tab, status, policyKey, from, to, cursor, selectedRun, selectedPolicy |
| `modules/policies/lib/policiesDisplay.ts` | Status tones, labels, relative time formatting, step type labels |
| `modules/policies/ui/PoliciesPage.tsx` | Page orchestrator — tab switching, shell region registration, renders active tab |
| `modules/policies/ui/RunsTab.tsx` | DataTable of execution runs with columns: ID, Lead, Policy, Status, When |

**Panel content (rendered by PoliciesPage):**
- Filter controls: Policy dropdown (from policies query), Status dropdown, From date, To date
- Apply / Reset buttons
- Summary section: Active policies count, runs in current view, failed count

**Behavior:**
- Filters use draft state pattern (match existing webhook page)
- Apply updates URL params, resets cursor
- Pagination: Next button when `nextCursor` exists, cursor stack for Prev
- Default sort: newest first (backend default)

**Done when:** Page loads at `/admin-ui/policies`, shows execution runs table, filters work, pagination works. Nav rail shows the new item.

---

## Stage 3 — Run Inspector

**Goal:** Clicking a run row opens the inspector with full detail and the step timeline — the key UX piece.

**Files to create:**

| File | Purpose |
|------|---------|
| `modules/policies/ui/RunInspector.tsx` | Run detail: status, outcome, lead, policy info, source |
| `modules/policies/ui/StepTimeline.tsx` | Vertical timeline of steps with icons, results, timestamps, connectors |

**Inspector shows:**

Top section — run metadata:
- Status (badge)
- Reason code / outcome
- Lead ID
- Policy key + version
- Source
- Event ID
- Created at

Bottom section — StepTimeline:
- Each step rendered as a vertical node with connector line
- Icon based on step status (checkmark/clock/spinner/x-circle/skip)
- Step label (human-readable type name)
- Result code
- Due at timestamp
- Completed at (updatedAt if terminal)
- Error message in red if present

**Behavior:**
- Click row → `selectedRun` in URL → detail query fires → inspector renders
- Click same row → deselects
- Inspector shows LoadingState while fetching

**Done when:** Selecting a run shows full detail with step timeline in the inspector. The timeline clearly communicates "what happened and when" for each step.

---

## Stage 4 — Manage Tab (Read-Only)

**Goal:** Second tab shows all policy versions. Selecting a policy shows its detail and parsed blueprint in the inspector. No mutations yet.

**Files to create:**

| File | Purpose |
|------|---------|
| `modules/policies/ui/ManageTab.tsx` | DataTable of policy versions: Key, Version, Status, Enabled |
| `modules/policies/ui/PolicyInspector.tsx` | Policy detail: domain, key, status, version, enabled + blueprint |
| `modules/policies/ui/BlueprintView.tsx` | Parsed blueprint display — steps as readable list, action config |

**Blueprint rendering (never raw JSON):**
- Parse `blueprint.steps[]` → show each step with type label and delay
- Parse `blueprint.actionConfig` → show action type and target
- Dependency chain shown as vertical connector between steps

**Behavior:**
- Tab switch to "Manage" → content shows policy table, panel shows domain filter
- Click policy row → `selectedPolicy` in URL → PolicyInspector renders
- Tab switching clears cross-tab selection (switching to Runs clears selectedPolicy, vice versa)

**Done when:** Both tabs work. Manager can browse runs AND policies, inspect both. Full read-only visibility of the system.

---

## Stage 5 — Policy Mutations

**Goal:** Create, edit, and activate policies through the UI.

**Files to create:**

| File | Purpose |
|------|---------|
| `modules/policies/ui/PolicyFormModal.tsx` | Modal for create + edit with step editor |
| `modules/policies/data/useCreatePolicyMutation.ts` | Create mutation, invalidates policy list |
| `modules/policies/data/useUpdatePolicyMutation.ts` | Update mutation, invalidates policy list |
| `modules/policies/data/useActivatePolicyMutation.ts` | Activate mutation, invalidates policy list |

**Create form fields:**
- Domain (text input)
- Policy Key (text input)
- Enabled (checkbox)
- Template (dropdown — only ASSIGNMENT_FOLLOWUP_SLA_V1 for now)
- Step 1 delay (number input, minutes)
- Step 2 delay (number input, minutes)
- Action type (dropdown: REASSIGN, MOVE_TO_POND)
- Target ID (number input — user ID or pond ID based on action type)

**Edit form:**
- Same as create but domain + policyKey are read-only
- Pre-filled from current policy data
- Sends `expectedVersion` for optimistic locking

**Activate:**
- Button in PolicyInspector (only for INACTIVE policies)
- ConfirmDialog before sending
- Sends `expectedVersion`

**Error handling:**
- Stale version → "Policy was modified, please refresh"
- Validation errors → inline field errors from blueprint validation codes
- Success → notification + list refresh + close modal

**Done when:** Full CRUD works. Create a policy, edit delays, activate a version — all from the UI.

---

## Stage 6 — Polish

**Goal:** Production-ready quality. Empty states, error handling, responsive behavior, and tests.

**Tasks:**

| Task | Detail |
|------|--------|
| Empty states | "No runs found" with contextual message, "No policies yet" with create prompt |
| Error states | Query errors render ErrorState in respective areas |
| Inspector empty | No inspector region registered when nothing selected (hides panel) |
| Responsive | Panel as drawer on mobile, inspector as drawer, tab bar scrollable |
| Loading states | Skeleton/spinner for table, inspector, modals |
| Notification messages | Success/error messages for all mutations |
| Tests | Unit tests for display helpers, search params, schema parsing. Component tests for key interactions (tab switch, filter apply, row select, step timeline rendering) |
| Validation | `npm run lint`, `npm run build`, `npm run test` all green |

**Done when:** Page handles all states gracefully, works on mobile, tests pass, build is clean.

---

## Stage Summary

| Stage | What You Get | New Files | Key Risk |
|-------|-------------|-----------|----------|
| 1 | API plumbing, all types flowing | ~10 | Schema mismatch with backend |
| 2 | Runs table with filters + pagination | ~4 | Cursor pagination UX |
| 3 | Run detail + step timeline | ~2 | Timeline component complexity |
| 4 | Policy list + blueprint view | ~3 | Blueprint parsing edge cases |
| 5 | Create / edit / activate | ~4 | Optimistic locking error UX |
| 6 | Production polish | ~0 (updates) | Test coverage scope |

Total: ~23 new files across 6 stages.
