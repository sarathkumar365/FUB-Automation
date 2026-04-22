# Workflow UX Audit & Redesign Tracker

**Scope:** workflow-related pages (list / detail / run list / run detail) + sidebar navigation.
**Explicitly out of scope:** building an authoring UI (drag-to-build, RJSF inspector, save-from-canvas). Create/update workflows stays on the existing JSON modal.
**Baseline:** 2026-04-21, branch `feature/workflow-builder-storyboard`.
**Preceded by:** [workflow-builder-ui-audit.md](./workflow-builder-ui-audit.md) (storyboard polish — closed 2026-04-21).

## Legend

- Status: `[ ]` todo · `[~]` in progress · `[x]` done · `[-]` dropped (with reason)
- Severity: **H** high-impact redesign · **M** medium restructure · **L** polish
- Gate per commit: `npm run lint && npm run build && npm run test`

---

## Motivation

Two pieces of UX feedback from the user (2026-04-21):

1. **Sidebar has two entries — "Workflows" + "Workflow Runs" — that are really one domain.** They belong under a single nav item with sub-tabs. A workflow and its runs are two views of the same thing.
2. **Run Detail is card-heavy and low-density.** Four stacked `PageCard`s: metadata (9 sparse rows), trigger payload (dominates vertical space), steps (pushed below the fold), plus a right-rail inspector that duplicates the main area (3 lines: ID / status / step count). The *story* of a run — what happened, in what order, with what outcome — is buried.

Storyboard + workflow-detail polish from the previous tracker already landed. This tracker covers what comes after.

---

## Locked decisions (2026-04-21)

| id | question | decision |
| --- | --- | --- |
| **D-Nav.1** | One sidebar entry or two? | **(A)** one entry "Workflows" with sub-tabs `Definitions` / `Runs`. Routes `/workflows` and `/workflow-runs` stay; they just render a shared sub-tab bar showing which is active. |
| **D-Run.1** | Run-detail layout pattern | **Pattern B** — hero + accordion steps + right summary rail. Based on Vercel / Sentry conventions. See `workflow-builder-ui-audit.md` companion research log for alternatives considered and ruled out. |
| **D-Run.2** | Step-row expansion behaviour | **(a)** expand inline (accordion). Click a step row → content opens below the row, not in a drawer or modal. |

---

## Pending decisions (locked 2026-04-21 — "go with defaults")

### Slice 1 — Navigation consolidation

| id | question | decision |
| --- | --- | --- |
| N1.1 | Sidebar entry label | **(a)** `Workflows` |
| N1.2 | Route strategy | **(a)** Keep both `/workflows` and `/workflow-runs` URLs; sub-tab bar renders on each page. Preserves bookmarks and `backTo` round-trips. |
| N1.3 | Sub-tab labels | **(a)** `Definitions` / `Runs` |
| N1.4 | Sub-tab bar on detail pages? | **(b)** No — detail pages own the whole surface; breadcrumb handles navigation back. |
| N1.5 | Existing `RunsTab` inside `WorkflowDetailPage` | **(a)** Keep — scoped-to-this-workflow is a distinct task from "what's failing across everything". |

### Slice 2 — Run Detail redesign

| id | question | decision |
| --- | --- | --- |
| R2.1 | Hero band content | **(a)** status + duration + workflow-key link + version + reason code (non-success only) + Cancel action top-right. |
| R2.2 | Step row collapsed content | **(a)** status dot + node id + step type + duration. |
| R2.3 | Step row expanded content | **(b)** 4-field summary (status / result code / duration / retry count) + outputs + errorMessage. Drop the 8-field grid. |
| R2.4 | Rail identifier grouping | **(b)** Two sections — `Identifiers` (run id / workflow key / version) and `Source` (lead id / event id). Timing lives in hero, not the rail. |
| R2.5 | Trigger payload placement | **(a)** Collapsed disclosure at the bottom of the rail. |
| R2.6 | Cancel action | **(a)** Hero top-right; disabled when not cancelable. |
| R2.7 | Auto-expand failing step? | **(a)** Yes. No auto-scroll — visual expansion is enough. |
| R2.8 | `?step=<nodeId>` deep-linking | **(b)** Defer — no concrete ask. Revisit if a user requests shareable step links. |
| R2.9 | Unify `WorkflowDetailPage/RunsTab` row layout with the new compact step preview? | **(a)** Yes — single row component, consistent across both surfaces. |

---

## Slice 1 — Navigation consolidation

**Goal:** one sidebar entry, two sub-tabs, zero broken bookmarks.

| id | status | task |
| --- | --- | --- |
| S1-A | [ ] | Collapse `appNavItems` in `ui/src/shared/constants/routes.ts` to remove `workflowRuns` as a top-level entry (per N1.1). |
| S1-B | [ ] | Add a new shared component `WorkflowsSubNav` (tabbed control, reuses `shared/ui/Tabs`) rendered at the top of `WorkflowsPage` and `WorkflowRunsPage` (per N1.2). Active sub-tab derived from current route. |
| S1-C | [ ] | Update `uiText.nav.*` keys for the new label scheme (per N1.3). |
| S1-D | [ ] | Decide sub-nav visibility on detail pages (per N1.4); if hidden, ensure breadcrumbs carry the user back. |
| S1-E | [ ] | Resolve `RunsTab` inside `WorkflowDetailPage` per N1.5. |
| S1-F | [ ] | Add Vitest coverage: sub-nav sync with route, active state reflects URL, keyboard arrow nav works. |
| S1-G | [ ] | Manual smoke: deep-link to `/admin-ui/workflow-runs` still works; deep-link to `/admin-ui/workflow-runs/:runId` still works; back-link from run detail still lands on the runs list with filters preserved. |

---

## Slice 2 — Run Detail redesign (Pattern B)

**Goal:** promote the step timeline to primary content; identifiers live in the shell inspector rail; payload collapses.

**Target layout (confirmed):**

```
┌─────────────────────────────────────────────────┬────────────────────┐
│ HERO                                            │  IDENTIFIERS       │
│ ● COMPLETED · 4m 12s · v3                       │  Run ID            │
│ WF: assignment_followup_sla_v1  (link)          │  Workflow key →    │
│ Reason code: sla_followup_sent (if non-success) │  Version           │
│                             [Cancel run ↗]      │  SOURCE            │
├─────────────────────────────────────────────────┤  Source lead ID    │
│ STEPS (7)                                       │  Event ID          │
│ ▸ ● entry · set_variable             2ms        │                    │
│ ▸ ● delay                            2m00s      │  TRIGGER PAYLOAD   │
│ ▾ ● wait_and_check_claim             2m10s      │  ▸ Expand JSON     │
│    status · FAILED     retry · 2                │                    │
│    result code · timeout                        │                    │
│    output { … }                                 │                    │
│    error: "timed out waiting for claim"         │                    │
│ ▸ ● branch_on_field                  1ms        │                    │
│ ▸ …                                             │                    │
└─────────────────────────────────────────────────┴────────────────────┘
```

| id | status | task |
| --- | --- | --- |
| S2-A | [ ] | Build `RunDetailHero` component using existing recipes (`Section`, `Badge`, `StatusBadge`). Consumes decisions R2.1, R2.6. |
| S2-B | [ ] | Rewrite `WorkflowStepTimeline` into accordion rows: collapsed content per R2.2, expanded content per R2.3, auto-expand rule per R2.7. Lift the inner `<details>` out — expansion is now row-level. |
| S2-C | [ ] | Replace the three-line shell-inspector body with a grouped identifier list (`KeyValueList` recipe, groups per R2.4) + trigger-payload disclosure per R2.5. |
| S2-D | [ ] | Delete `MetadataRow` + the 4-card layout from `WorkflowRunDetailPage.tsx`; wire in Hero + Timeline + rail. |
| S2-E | [ ] | If R2.8 = (a): read `?step=<nodeId>` on mount, scroll into view, auto-expand that row. Else skip. |
| S2-F | [ ] | If R2.9 = (a): rebuild `WorkflowDetailPage/RunsTab`'s row layout to match the new compact row. |
| S2-G | [ ] | Vitest: hero renders reason code only on non-success; cancel button visible only when cancelable; step row expansion toggles on click + Enter/Space; rail identifier groups present; payload disclosure closed by default. |
| S2-H | [ ] | Manual smoke: failing run (auto-expanded failing step), running run (cancel action live), terminal run (no cancel), empty-payload run (disclosure hidden or empty state). |

---

## Execution ordering

1. **Slice 1 first** — small, mechanical, no risky redesign. Unblocks the "one domain" feeling before we dig into Run Detail.
2. **Slice 2 second** — bigger visual rework; needs all R2.* decisions locked before touching code.

Each slice: decisions lock → code → new test → lint/build/test → commit. No unilateral decisions inside a locked slice.

---

## Validation gates per slice

- `cd ui && npm run lint`
- `cd ui && npm run build`
- `cd ui && npm run test`
- New/updated tests for any behaviour change (per AGENTS.md §Test and validation policy).
