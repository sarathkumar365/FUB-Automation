# UI Architecture Audit — `ui/`

Date: 2026-06-18
Scope: BROAD — the entire `ui/` React submodule (file organization, layering, inter-component
communication, state management, routing, error handling, tooling, testing).
Method: structural read of all of `src/` + four parallel deep-dives (platform / modules / app+shared /
cross-cutting), each finding verified against source with `file:line` evidence.

---

## Verdict

This is a **mature, well-architected frontend** — above the bar for an internal admin UI. It implements a
clean hexagonal **ports-and-adapters** boundary, a documented **tier model** for shared UI, strict
**token discipline** (no raw hex; dark mode for free), **strict TypeScript with zero `any`**, and a
comprehensive `npm run check` gate (lint + token-guard + knip dead-code + build + ~90 tests).

The findings below are almost all **maintainability / coherence**, not correctness. There are no
architectural defects that produce wrong behavior. The two worth acting on first are *import ergonomics*
(no path aliases → 5–6 level `../` chains) and a *dependency-direction inconsistency* in the platform layer.

---

## Architecture at a glance

```
src/
├── app/         route + shell + provider composition, error boundaries, shell-regions context
├── platform/    hexagonal boundary: ports/ (contracts) + adapters/ (http, sse) + query/ + stream/ + container
├── modules/     11 feature modules, each ~ data/ (hooks) + lib/ (pure) + ui/ (views)
│   └── workflows-builder/  expanded: model/ + state/ (zustand) + surfaces/ + observability/
├── shared/      ui/ (primitives + recipes), constants/ (uiText, routes, queryDefaults),
│                notifications/, theme/, lib/, types/
└── styles/      tokens.css (single source for color/spacing; dark theme remaps the same names)
```

**Layer dependency rule (intended):** `app → modules → platform → (transport)`, with `shared` usable by all.
DI flows through a `container.ts` singleton exposed via React context (`useAppPorts()`); modules consume
**ports**, never adapters. This is sound and consistently followed on the consumer side.

**State is cleanly partitioned across four mechanisms** — no overlap, no server-state-copied-into-local
anti-pattern was found:

| State | Mechanism | Where |
|---|---|---|
| Server state | TanStack Query | `modules/*/data/use*Query.ts` |
| Builder graph | Zustand (`builderStore`) | `workflows-builder/state/` only — not mounted on read-only pages |
| Infra (DI, layout regions, toasts, theme) | React Context | `app/portsContext`, `app/shellRegionsContext`, `shared/notifications`, `shared/theme` |
| Ephemeral UI | `useState` | feature components |

**Inter-component communication** is principled: props down, query cache as the shared server-state bus,
shell Panel/Inspector content published via `useShellRegionRegistration` (memoized, with unmount cleanup),
and the heavy storyboard surface is a **single reusable component** (`workflows-builder/surfaces/storyboard`)
consumed by both the builder and the workflow detail page — no duplication.

---

## Findings (ranked)

Severity reflects **maintainability/risk over a 6-month horizon**, not runtime breakage (there is none).

### 1. No path aliases → 5–6 level relative-import chains — **HIGH (maintainability)**
- **What:** No `paths`/`baseUrl` in `tsconfig.app.json`, no `resolve.alias` in `vite.config.ts`. **31 files**
  import with 5+ `../` hops; 52 with 4+. Deepest live in
  `modules/workflows/ui/WorkflowDetailPage/StoryboardTab/inspector/*`, e.g.
  `ConfigRow.tsx` reaching `../../../../../../shared/ui`.
- **Why it matters:** Moving any file in those subtrees rewrites every relative import; IDE hints truncate;
  readers count `../` by hand. This is the single largest day-to-day friction in the codebase.
- **Fix:** Add `@/*`, `@shared/*`, `@platform/*`, `@modules/*` aliases to `tsconfig.app.json` + `vite.config.ts`
  (Vitest inherits the Vite config), then codemod the deep chains. Mechanical, low-risk, high payoff.

### 2. Dependency-direction inconsistency: `platform/` imports schemas from `modules/` — **HIGH (coherence)**
- **What:** Platform ports and adapters import Zod schemas/types from feature modules at **8 sites**:
  - `platform/ports/workflowPort.ts:1-9`, `workflowRunPort.ts`, `settingsPort.ts`
  - `platform/adapters/http/httpWorkflowAdapter.ts:1-8`, `httpWorkflowRunAdapter.ts`, `httpSettingsAdapter.ts`
  (the last also imports `projectSettingsConfig` business logic from `modules/settings/lib`).
- **Why it matters:** It inverts the intended `modules → platform` direction. It's also **internally
  inconsistent**: webhook / person / processed-call schemas correctly live in `platform/adapters/http/*Schemas.ts`,
  but workflow / workflow-run / settings schemas live in `modules/*/lib`. Two conventions for the same job.
- **Note / nuance:** The `auth` token-store imports in `httpJsonClient.ts:2` and `sseWebhookStreamAdapter.ts:5`
  are a *different* case — a justified cross-cutting concern (the transport genuinely needs the token). Leave
  those; just document them as the sanctioned exception.
- **Fix (pick one and make it the rule):**
  - **A — schemas are platform-owned:** move workflow/workflowRun/settings schemas into
    `platform/adapters/http/`, modules import the *types* back from platform. Matches webhooks/persons today.
  - **B — schemas live with their module, platform depends inward by design:** keep as-is but record it as an
    accepted decision and migrate webhooks/persons to match, so there's one convention.
  Either is defensible; the cost today is having *both*.

### 3. No code-splitting — `workflows-builder` + `@dagrejs/dagre` in the main bundle — **MEDIUM**
- **What:** No `React.lazy()` anywhere; `router.tsx` imports every page eagerly, including the heavy graph
  builder and its `@dagrejs/dagre` dependency.
- **Why it matters:** View-only users (dashboard, webhooks, calls) download the full builder + dagre on first load.
- **Fix:** `React.lazy()` the builder route(s) behind a `Suspense` with the existing `LoadingState` fallback.
  Largest single win for initial load; the rest can stay eager.

### 4. No module-boundary enforcement in lint — **MEDIUM**
- **What:** Cross-module imports and the "Zustand store stays inside `workflows-builder`" rule are honored by
  discipline only. No `eslint-plugin-import` / `no-restricted-imports` guarding the layer graph.
- **Why it matters:** The layering is correct *today*; nothing stops the next change from importing `builderStore`
  into `auth`, or an adapter into a view. Tooling already enforces tokens and dead code — boundaries are the gap.
- **Fix:** Add `no-restricted-imports` zones: views can't import adapters; only `workflows-builder` imports its
  store; `platform` can't import `modules` (encodes finding #2 once resolved).

### 5. Hardcoded query keys bypass the `queryKeys` factory — **MEDIUM**
- **What:** Three invalidation callbacks hand-write keys instead of using `platform/query/queryKeys.ts`:
  `workflowMutationInvalidation.ts:6` (`['workflows','list']`), `workflowRunMutationInvalidation.ts:13,16`,
  `useReplayProcessedCallMutation.ts:29`.
- **Why it matters:** If the key shape changes in the factory, these silently stop invalidating → stale UI.
- **Fix:** Route them through `queryKeys` (add a no-filter list variant if needed).

### 6. `AGENTS.md` module list is stale — **MEDIUM (doc drift in the canonical conventions file)**
- **What:** `ui/AGENTS.md:33-38` still describes module boundaries as only `webhooks` + `processed-calls`. There
  are now **11 modules** (auth, dashboard, landing, persons, settings, workflows, workflows-builder,
  workflow-runs, …). The "Step 1 status snapshot" is frozen at 2026-03-19.
- **Why it matters:** This is the file agents/contributors are told to read first; drift here propagates into
  every future change.
- **Fix:** Refresh the module list and the layer-rules section; fold the resolution of #2 and #4 into it.

### 7. Misplaced hook: `useWorkflowDetailActions.ts` lives in `lib/` — **LOW**
- **What:** `modules/workflows/ui/WorkflowDetailPage/lib/useWorkflowDetailActions.ts` uses React hooks
  (`useState`/`useCallback`) but sits in a `lib/` folder reserved for pure helpers.
- **Fix:** Move to `data/` (or alongside the page as a hook). Pure-`lib` should never import React.

### 8. Cross-module type ownership blur — **LOW**
- **What:** `dashboard` imports `WorkflowRunSummary` from `workflows/lib/workflowSchemas` though `workflow-runs`
  is the domain owner; run/summary types are split between `workflows` and `workflow-runs`.
- **Fix:** Consolidate run types under `workflow-runs` (or `shared/types`) so there's a single owner. Low urgency.

### 9. Recipes hardcode user-facing `"Yes"/"No"` — **LOW (i18n blocker)**
- **What:** `shared/ui/recipes/FieldRow.tsx:53` and `KeyValueList.tsx:42` emit literal `'Yes'/'No'`, bypassing
  the otherwise-strict `uiText` centralization.
- **Fix:** Add `uiText.common.yes/no`. Only matters if i18n is ever on the table; flagged for completeness.

### 10. `.join(' ')` instead of `cn()` for class composition — **LOW**
- **What:** `shared/ui/PanelNav.tsx` and `AppRail.tsx` build `className` with `.join(' ')` rather than the
  project's `cn()` (clsx + tailwind-merge), losing conflict de-duplication.
- **Fix:** Swap to `cn()` for consistency/robustness.

### 11. Test layout: flat `src/test/` + a few coverage gaps — **LOW**
- **What:** ~90 tests, all in a flat `src/test/` dir (none colocated). The flat layout is **fine** at this scale.
  Gaps: `WorkflowRunDetailPage`, `PersonsPage`, `ProcessedCallsPage`, and some `workflows-builder` storyboard
  interaction paths lack dedicated coverage.
- **Fix:** Backfill tests for the named detail/list pages; no need to restructure the directory.

### 12. `WorkflowsPage.tsx` ~366 LOC — **LOW**
- **What:** Largest page component; mixes filter state (fragmented `useState`s) + column defs + table + modal +
  shell registration.
- **Fix:** Extract `useWorkflowsTableColumns` and consolidate filter state into one hook. Optional.

---

## What's notably good (keep doing)

- **Hexagonal boundary with DI via context** — consumers depend on ports only; adapters are swappable.
- **Zod validation at every HTTP boundary**; SSE validated at the consumer (`useWebhookStream`) — correct.
- **Tier model for `shared/ui`** (primitives → recipes → feature) is real and adhered to; barrel exports complete;
  `StatusBadge`→`badge` is composition, not duplication.
- **Token discipline** enforced by `check-hex.mjs`; dark mode is automatic via `:root[data-theme="dark"]`.
- **Single reusable storyboard surface** shared by builder + detail page — no graph-rendering duplication.
- **Strict TS, zero `any`**, discriminated-union reducer in the Zustand store.
- **`npm run check`** bundles lint + token-guard + knip + build + test into one gate.

---

## Suggested action order

1. **#1 path aliases** + **#6 refresh AGENTS.md** — cheap, unblock/clarify everything else.
2. **#2 schema ownership decision** → record it, then **#4 lint boundary rules** to enforce it.
3. **#5 query-key factory** + **#7 misplaced hook** — small correctness-adjacent hygiene.
4. **#3 lazy-load the builder** — performance win when convenient.
5. **#8–#12** — opportunistic, as the relevant files are touched.

## Decisions this audit surfaces (candidates for `Docs/repo-decisions/`)

- **RD-?: Schema ownership** — platform-owned vs module-owned-with-inward-dependency (finding #2). Needs a call.
- **RD-?: Module-boundary lint zones** — encode the layer graph in `eslint` (finding #4).

## Non-goals / not assessed

- Visual/UX fidelity vs the design system (separate from architecture).
- Runtime performance profiling (no measurements taken; bundle note in #3 is static-analysis only).
- Backend contract correctness (UI consumes HTTP contracts as-is by design).
