# Phase 1 — Low-risk hygiene + unblockers (detailed plan)

Covers **UAC-01, UAC-05, UAC-06, UAC-07, UAC-09, UAC-10**. None are decision-gated. No product behavior
changes. Each step is its own reviewable commit; `npm run check` must be green before the next.

> Per project rule (memory: *review before commit*): implement → present → wait for OK. Tests/build may run
> freely; only commits wait.

## Locked decisions (2026-06-18)
- **Hook home (UAC-07):** co-locate at `WorkflowDetailPage/useWorkflowDetailActions.ts` (out of `lib/`).
- **Alias set (UAC-01):** layered — `@app/* @platform/* @modules/* @shared/* @styles/*` (+ `@/*` root).
- **Sweep scope (UAC-01):** thorough — convert all cross-layer relatives (`modules → shared|platform|app|
  other-module`) to aliases repo-wide; keep intra-feature `./` and single `../`.

## Recommended commit order

Small semantic fixes first (clean, easy to review), then the mechanical alias sweep last so it doesn't bury
the others in review noise.

| # | Commit | Finding | Risk | ~Files |
|---|--------|---------|------|--------|
| 1 | `refactor(ui): route mutation invalidations through queryKeys factory` | UAC-05 | low | 4 |
| 2 | `refactor(ui): move useWorkflowDetailActions out of lib/` | UAC-07 | low | 4 |
| 3 | `refactor(ui): source Yes/No from uiText in recipes` | UAC-09 | low | 3 |
| 4 | `refactor(ui): use cn() for class composition in PanelNav/AppRail` | UAC-10 | low | 2 |
| 5 | `docs(ui): refresh AGENTS.md module list` | UAC-06 | low | 1 |
| 6 | `refactor(ui): add path aliases, convert deep relative imports` | UAC-01 | mechanical | ~14 + 2 config |

---

## Step 1 — UAC-05: query keys through the factory

**Root nuance:** the three hardcoded arrays are intentional **prefixes** (TanStack matches `['workflows','list']`
against every `['workflows','list',<filters>]`). So we add prefix helpers — `list(filters)` alone wouldn't
invalidate all filter variants.

**`platform/query/queryKeys.ts` — add:**
- `workflows.lists: () => ['workflows', 'list'] as const`
- `workflowRuns.lists: () => ['workflow-runs', 'list'] as const`
- `workflowRuns.forKey: (key: string) => ['workflow-runs', 'key', key] as const`
- `processedCalls.lists: () => ['processed-calls', 'list'] as const`

**Swap call sites:**
- `modules/workflows/data/workflowMutationInvalidation.ts:6` → `queryKeys.workflows.lists()`
- `modules/workflow-runs/data/workflowRunMutationInvalidation.ts:13` → `queryKeys.workflowRuns.lists()`
- `…:16` → `queryKeys.workflowRuns.forKey(updatedRun.workflowKey)`
- `modules/processed-calls/data/useReplayProcessedCallMutation.ts:29` → `queryKeys.processedCalls.lists()`

**Tests:** add a small `queryKeys` unit test asserting the new prefix helpers produce the exact arrays the old
code used (guards against drift). Re-run `workflow-mutation-hooks`, `workflow-runs-hooks`, processed-calls tests.
**Acceptance:** no hand-written key arrays in those 3 files; suite green.
**Note:** `workflowRunMutationInvalidation.ts` also imports a type from `workflows/lib` — that's UAC-02/UAC-08;
**leave it** this phase.

---

## Step 2 — UAC-07: move the misplaced hook

`WorkflowDetailPage/lib/useWorkflowDetailActions.ts` uses React hooks but sits in `lib/` (pure-only). It also
exports the `ValidationViewState` type consumed by `StoryboardTab/index.tsx` and `StoryboardTab/ValidationStrip.tsx`.

**Decided:** co-locate at `WorkflowDetailPage/useWorkflowDetailActions.ts` (drop the `lib/` subfolder for it;
`lib/parseWorkflowGraph.ts` stays — it's genuinely pure).

**Importers to update (`./lib/…` → `./…`, `../lib/…` → `../…`):**
`WorkflowDetailPage/index.tsx:34`, `StoryboardTab/index.tsx:22`, `StoryboardTab/ValidationStrip.tsx:12`.
**Acceptance:** no React imports remain under any `lib/`; knip + suite green.

---

## Step 3 — UAC-09: Yes/No from uiText

`uiText.common` already exists (`shared/constants/uiText.ts:541`). **Add** `yes: 'Yes'`, `no: 'No'`.
**Update** `shared/ui/recipes/FieldRow.tsx:53` and `KeyValueList.tsx:42` to
`value ? uiText.common.yes : uiText.common.no`.
**Tests:** existing `shared-ui-recipes-field-row` / `…-key-value-list` already assert `getByText('Yes'/'No')` —
they **pass unchanged** because the values stay "Yes"/"No", now sourced centrally.
**Acceptance:** no user-facing literals in those recipes; suite green.

---

## Step 4 — UAC-10: cn() for class composition

Replace `[ … ].join(' ')` with `cn( … )` (import from `shared/lib/cn`) in:
`shared/ui/PanelNav.tsx:21-26`, `shared/ui/AppRail.tsx:43-48`.
**Tests:** existing nav tests cover active/inactive; add an assertion only if needed.
**Verify:** preview — active/inactive nav states render identically (visual parity).
**Acceptance:** both use `cn()`; `lint:tokens` + suite green.

---

## Step 5 — UAC-06: refresh AGENTS.md module list

`ui/AGENTS.md:33-38` (and the mirrored list ~line 80) name only `webhooks` + `processed-calls`. Update to the
real 11: auth, dashboard, landing, persons, settings, webhooks, processed-calls, workflows, workflows-builder,
workflow-runs (+ note `workflows-builder`'s expanded layout: `model/ state/ surfaces/ observability/`).
**This pass = factual list only.** The convention edits (schema ownership, boundary rules) are re-touched in
Phase 3 once those decisions land. Leave a marker: "layer/boundary rules finalized in Phase 3 (UAC-04)."
**Acceptance:** module list matches reality; no contradiction with `src/shared/ui/README.md`.

---

## Step 6 — UAC-01: path aliases + deep-import conversion (mechanical, last)

**Config:**
- `tsconfig.app.json` → add `"baseUrl": "."` and `"paths"`.
- `vite.config.ts` → matching `resolve.alias` (Vitest inherits this config — no separate test wiring).

**Alias set (decided): layered** — `@app/* @platform/* @modules/* @shared/* @styles/*` (+ `@/*` root). Pairs
with Phase 3 boundary lint (named zones) and makes layer crossings legible.

**Conversion scope (decided): thorough** — convert all **cross-layer** relatives
(`modules → shared|platform|app|other-module`) to aliases repo-wide; keep intra-feature `./`/single `../`.
Eliminates mixed-style imports. Do as this one isolated mechanical commit.

**Guardrail (lock the win):** add a lightweight ESLint `no-restricted-imports` pattern banning `../../../../`
(4+ levels) going forward. Full layer-zone rules are Phase 3 (UAC-04); this is just the depth backstop.

**Acceptance:** zero imports with 4+ `../`; `tsc -b` + `vite build` + suite all green; app boots in preview.
**Risk:** large but mechanical — rely on `tsc` (strict) + tests; isolate as its own commit for clean revert.

---

## Phase-1 done signal
All six tracker cards ☑; README status board shows 6/12; `npm run check` green; preview boots clean;
implementation-log.md has an entry per step. Open decisions resolved: hook destination (Step 2), alias-set +
conversion-scope (Step 6).
