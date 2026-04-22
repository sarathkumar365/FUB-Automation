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

## Pending decisions (lock before implementation)

### Slice 1 — Navigation consolidation

| id | question | options | status |
| --- | --- | --- | --- |
| N1.1 | What label for the unified sidebar entry? | (a) `Workflows`, (b) `Workflows & Runs`, (c) `Automations` | _TBD_ |
| N1.2 | Route strategy? | (a) Both `/workflows` and `/workflow-runs` keep their URLs; sub-tab bar renders on each page (cheapest, preserves bookmarks), (b) collapse to `/workflows?view=definitions` and `/workflows?view=runs` (cleaner URL but breaks deep-links), (c) new `/workflows` landing that redirects to the selected sub-tab | _TBD_ |
| N1.3 | Sub-tab labels? | (a) `Definitions` / `Runs`, (b) `All Workflows` / `All Runs`, (c) `Workflows` / `Runs` | _TBD_ |
| N1.4 | When a user is on a workflow detail page (`/workflows/:key`), is the sub-tab bar shown? | (a) yes — stays visible so they can jump back to the Definitions list, (b) no — detail pages own the whole surface; breadcrumb handles navigation back | _TBD_ |
| N1.5 | What happens to the existing `RunsTab` inside `WorkflowDetailPage`? | (a) keep — it's scoped to this workflow and is useful in context, (b) remove and direct users to the global Runs sub-tab pre-filtered by workflow key, (c) keep but rebrand to match the new Pattern B language (e.g. add hero strip for the selected workflow) | _TBD_ |

### Slice 2 — Run Detail redesign

| id | question | options | status |
| --- | --- | --- | --- |
| R2.1 | What goes in the **hero band**? | (a) status + duration + workflow-key link + version + reason code (failure only); Cancel action top-right when cancelable, (b) the above + source lead ID chip, (c) status + duration only, everything else to the rail | _TBD_ |
| R2.2 | **Step row collapsed** content | (a) status dot + node id + step type + duration, (b) status dot + step type + timestamp (started or completed, whichever is latest), (c) status dot + node id + result code | _TBD_ |
| R2.3 | **Step row expanded** content | (a) current 8-field grid (node id / step type / status / result code / retry count / due at / started / completed) + outputs + errorMessage, (b) 4-field summary (status / result code / duration / retry count) + outputs + errorMessage, (c) same as (b) plus a raw-JSON toggle | _TBD_ |
| R2.4 | Rail **grouping** of identifiers | (a) one flat list, (b) two sections: `Identifiers` (run id / workflow key / version) and `Source` (lead id / event id), (c) three sections: adds `Timing` for started/completed (though those also live in the hero) | _TBD_ |
| R2.5 | **Trigger payload** placement | (a) collapsed disclosure at the bottom of the rail, (b) separate collapsed section below the step list in the main column, (c) dedicated tab inside the hero | _TBD_ |
| R2.6 | **Cancel** action placement | (a) hero top-right, disabled when not cancelable, (b) stay inside steps card, (c) shell-level action menu | _TBD_ |
| R2.7 | On **failure** runs — should the failing step auto-expand? | (a) yes, (b) no — user clicks to expand, (c) yes + scroll into view | _TBD_ |
| R2.8 | Should scroll-anchor **deep-linking to a specific step** (`?step=<nodeId>`) be supported? | (a) yes, (b) no — deferred until requested | _TBD_ |
| R2.9 | Workflow-detail page already has **its own RunsTab** — should its row layout adopt the same step-preview compact pattern we're building for Run Detail? | (a) yes — unified look, (b) no — list stays terse, detail is where density changes | _TBD_ |

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
