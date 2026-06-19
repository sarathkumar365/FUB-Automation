# Phase 5 (final) — Test backfill (UAC-11) + decompose WorkflowsPage (UAC-12)

Goal: close the last two findings → **13/13**. Two synergistic LOW-severity items: decomposing `WorkflowsPage`
(UAC-12) produces directly-testable units that also serve the coverage backfill (UAC-11). No behavior change.
Per review-before-commit: implement → present → wait.

---

## Correction: the audit's UAC-11 list was stale
The audit named run-detail / persons / processed-calls as untested. Re-checked against the current suite — the
real picture:

| Page | Dedicated test? |
|---|---|
| WorkflowRunDetailPage | ✅ `workflow-run-detail-page.test` (since Phase 1) |
| ProcessedCallsPage | ✅ `processed-calls-page.test` |
| PersonsPage (list) | ❌ **gap** (only `PersonDetailPage` is tested) |
| WorkflowBuilderPage | ❌ **gap** (storyboard *surface* units exist; the page itself has none) |
| WorkflowsPage | ⚠️ `workflows-page-selection.test` covers selection + the catalog panel, **not** filter apply/reset or columns |

So the **real UAC-11 gaps**: `PersonsPage`, `WorkflowBuilderPage`, and `WorkflowsPage` filter behavior.
(Storyboard *interaction* is already covered by `workflow-detail-scene-selection`, `workflow-scene-inspector-popover`,
`workflow-storyboard-scene-selected-ring`, etc. — not a gap.)

---

## UAC-12 — decompose `WorkflowsPage` (366 LOC)

The page mixes five concerns: filter draft-state, table columns, shell panel/inspector, create-modal, and the
list render. The two worth extracting (per the audit) — both currently inline and untestable in isolation:

1. **Table columns** (lines 72-101, `ColumnDef<WorkflowResponse>[]`, **zero reactive deps**) → extract to a
   co-located **`workflowsColumns.tsx`** as a module const (`workflowColumns`). It returns JSX renderers, so it
   belongs in `ui/`, not `lib/`. (A hook isn't needed — deps are `[]`; a const is simpler and the audit's
   "useWorkflowsTableColumns" intent is satisfied by a directly-importable const.)
2. **Filter draft-state + apply/reset** (lines 50-55, 148-169, and the `Select onChange` at 233-240) → extract
   to a co-located page-local hook **`useWorkflowsFilters.ts`** returning `{ draftFilters, setStatus, apply,
   reset }`. It owns the `searchParams`-vs-draft reconciliation (the `filterDraftKey` keying that resets the
   draft when the applied status changes). Page-local hook beside the page — same placement convention as
   `useWorkflowDetailActions` (AGENTS rule).

Leave as-is (already clean): `CatalogSection`, `ControlGroup`, `buildInspectorBody` (already extracted local
helpers); the panel/inspector `useMemo`s (shell glue); the create-modal handler. Optionally move
`buildInspectorBody`/`CatalogSection` to co-located files — **out of scope** (no real gain).

**Expected:** WorkflowsPage ~366 → ~210 LOC; behavior identical (the existing 4-case selection test +
the full suite stay green and prove it).

**Noted, out of scope:** `PersonsPage` uses the **same** filter-draft + columns + URL-search pattern. A shared
`useListFilters`/filterable-list abstraction would dedupe both — but that's a separate refactor, not UAC-12.
Flagging so the duplication is a conscious deferral, not an oversight.

---

## UAC-11 — backfill the real gaps

Characterization-first so the UAC-12 refactor is protected:

1. **`workflows-page-filters.test`** (NEW) — render `WorkflowsPage`, assert: changing the status Select +
   Apply updates the query/URL (page resets to 0, selectedKey cleared); Reset restores ALL. **Write this
   BEFORE the UAC-12 refactor** — it characterizes the behavior the extraction must preserve.
2. **`useWorkflowsFilters` unit test** (NEW, after extraction) — draft keying (draft resets when applied
   status changes), `apply`/`reset` produce the right search-param state. Pure-ish hook → `renderHook`.
3. **`persons-page.test`** (NEW) — the named gap. Render `PersonsPage`: rows render; filter apply; row click
   → `navigate(routes.personDetail(id))`; cursor pagination; loading / error / empty states. Mirror the
   structure of the existing `processed-calls-page.test`.
4. **`workflow-builder-page.test`** (NEW) — light smoke for the gap: renders the storyboard for a valid
   workflow (mocked detail query); shows error/loading states. Import the page **directly** (the Phase-4 lazy
   boundary only affects the router, not a direct test import). Keep light — a heavier interactive builder is
   planned later and will bring its own tests.

`workflowColumns` is exercised via the WorkflowsPage render tests (its renderers run there); a separate column
unit test is optional, not required.

---

## Order of work (each step `npm run check` green)
1. **Add `workflows-page-filters.test`** (characterize current filter behavior).
2. **UAC-12 extract** `workflowColumns` + `useWorkflowsFilters`; rewire WorkflowsPage; existing selection test +
   the new filters test stay green (proves behavior-preserving).
3. **Add `useWorkflowsFilters` unit test.**
4. **Add `persons-page.test`** (gap).
5. **Add `workflow-builder-page.test`** (gap).
6. Docs: tick UAC-11 + UAC-12; flip README to ✅ 13/13 Complete; append implementation-log; refresh the
   project memory.

## Risks
- **Refactor regressions** → mitigated by characterization test (step 1) + the existing selection test; the
  decomposition is pure extraction (no logic change).
- **Filter draft-keying subtlety** — the `draftFilterState.key === filterDraftKey` reconciliation (resets the
  draft when the *applied* status changes out from under it) is the one non-obvious bit; the hook must
  preserve it exactly. The unit test (step 3) pins it.
- **PersonsPage test breadth** — keep it behavior-focused (render/filter/navigate/paginate/states), not
  snapshot; mirror the existing list-page tests' style.
- **Test count** — adds ~4 test files; `npm run check` stays the gate.

## Acceptance
- `WorkflowsPage` materially smaller; `workflowColumns` + `useWorkflowsFilters` extracted and (filters) unit-tested.
- `PersonsPage` + `WorkflowBuilderPage` have dedicated behavior tests; `WorkflowsPage` filter apply/reset covered.
- `npm run check` green (≥396 + new tests). Behavior unchanged.
- UAC-11 + UAC-12 ticked → **13/13**; README flips to ✅ Complete; memory updated.

## Non-goals
- A shared filterable-list abstraction across WorkflowsPage/PersonsPage (separate refactor).
- Heavy WorkflowBuilderPage tests (defer to the planned interactive builder).
- Moving already-clean local helpers (`CatalogSection`, etc.) to separate files.
- Raising overall coverage targets / adding e2e — out of scope.
