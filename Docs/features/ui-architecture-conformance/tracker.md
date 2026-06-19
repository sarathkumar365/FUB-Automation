# UI Architecture Conformance — Issue Tracker

Canonical, per-finding list. Update the **Status** line of each card as work proceeds. IDs are stable.
Source: [UI Architecture Audit 2026-06-18](../../audits/ui-architecture-audit-2026-06-18.md).

Status legend: ☐ Open · ◐ In progress · ☑ Done · ⊘ Won't fix

---

## UAC-01 — No path aliases → deep relative-import chains
- **Status:** ☑ Done (2026-06-18, Phase 1 — layered aliases, thorough cross-layer sweep, lint backstop)
- **Severity:** HIGH (maintainability)
- **Rule violated:** "Prefer modular design… extract reusable logic"; general maintainability. No alias config
  exists, so refactors are brittle.
- **Evidence:** No `paths`/`baseUrl` in `ui/tsconfig.app.json`; no `resolve.alias` in `ui/vite.config.ts`.
  **31 files** import with 5+ `../`; **52** with 4+. Deepest:
  `ui/src/modules/workflows/ui/WorkflowDetailPage/StoryboardTab/inspector/ConfigRow.tsx` →
  `../../../../../../shared/ui`.
- **Fix:** Add aliases `@/*`, `@shared/*`, `@platform/*`, `@modules/*`, `@app/*` to `tsconfig.app.json` +
  `vite.config.ts` (Vitest inherits Vite config). Codemod 4+ level relative imports to aliases. Keep
  intra-feature short relatives (`./`, `../`) as-is.
- **Acceptance:** zero imports with 4+ `../`; `npm run check` green; ESLint may add `no-restricted-imports`
  pattern to ban `../../../../` going forward (overlaps UAC-04).
- **Risk:** large mechanical diff. Mitigate: do as its own commit, rely on `tsc` + tests to catch breakage.

---

## UAC-02 — `platform/` imports schemas/logic from `modules/` (dependency inversion + inconsistency)
- **Status:** ☐ Open
- **Severity:** HIGH (coherence)
- **Rule violated:** `ui/AGENTS.md` layer rule `modules → platform` (platform is the inner contract layer);
  "platform: adapters, transport, query wiring, stream contracts".
- **Evidence (8 sites):**
  - `ui/src/platform/ports/workflowPort.ts:1-9`, `workflowRunPort.ts:1-5`, `settingsPort.ts:1`
  - `ui/src/platform/adapters/http/httpWorkflowAdapter.ts:1-8`, `httpWorkflowRunAdapter.ts:1-4`,
    `httpSettingsAdapter.ts:1-5` (also imports `projectSettingsConfig` **business logic** from
    `modules/settings/lib/settingsProjection`).
  - Inconsistency: webhook/person/processed-call schemas correctly live in
    `platform/adapters/http/*Schemas.ts`; workflow/workflow-run/settings schemas live in `modules/*/lib`.
- **Justified exception (do NOT change):** `httpJsonClient.ts:2` and `sseWebhookStreamAdapter.ts:5` import the
  auth token store — legitimate cross-cutting transport concern. Document it as sanctioned.
- **Decision required (RD):** pick ONE and apply uniformly:
  - **A — platform-owned schemas:** move workflow/workflowRun/settings schemas (+ settings projection) into
    `platform/adapters/http/`; modules import types back from platform. Matches webhooks/persons today.
  - **B — module-owned, platform depends inward by design:** keep, record as accepted, migrate webhooks/persons
    to match so there is ONE convention.
- **Acceptance:** exactly one schema-ownership convention across all domains; `grep` shows no *unsanctioned*
  `platform/ → modules/` imports (auth exception documented); RD written + Accepted; `npm run check` green.

---

## UAC-03 — No code-splitting; builder + dagre always bundled
- **Status:** ☐ Open
- **Severity:** MEDIUM (performance)
- **Evidence:** No `React.lazy()` anywhere; `ui/src/app/router.tsx` imports every page eagerly, including
  `WorkflowBuilderPage` which pulls `@dagrejs/dagre`.
- **Fix:** `React.lazy()` the builder route(s) (`workflows/new`, `workflows/:key/edit`) behind `Suspense` with
  the existing `LoadingState` fallback. Optionally a manualChunks split for dagre.
- **Acceptance:** builder + dagre absent from the initial chunk (verify via `vite build` output); view-only
  routes (dashboard/webhooks/calls) load without them; `npm run check` green; a test asserts the lazy route
  renders behind Suspense.
- **Note:** only worth doing if view-only users are a real load-time audience — confirm before P4.

---

## UAC-04 — No lint enforcement of module/layer boundaries
- **Status:** ☐ Open
- **Severity:** MEDIUM
- **Evidence:** `ui/eslint.config.js` has no `no-restricted-imports`/`eslint-plugin-import` boundary zones.
  Layering and "Zustand store stays inside workflows-builder" are honored by discipline only.
- **Decision required (RD):** the layer graph to encode (`app → modules → platform`; `shared` open;
  views may not import adapters; only `workflows-builder` imports `builderStore`; `platform` may not import
  `modules` except the documented auth exception).
- **Fix:** add `no-restricted-imports` zones (and/or `eslint-plugin-import` `no-restricted-paths`). Must land
  **after** UAC-02 so the platform↔modules rule reflects the chosen convention.
- **Acceptance:** lint fails on a deliberately-wrong cross-layer import (add a smoke check); RD Accepted;
  `npm run check` green.

---

## UAC-05 — Hardcoded query keys bypass the `queryKeys` factory
- **Status:** ☑ Done (2026-06-18, Phase 1)
- **Severity:** MEDIUM
- **Rule violated:** centralized query-key management (`platform/query/queryKeys.ts`).
- **Evidence:**
  - `ui/src/modules/workflows/data/workflowMutationInvalidation.ts:6` — `['workflows','list']`
  - `ui/src/modules/workflow-runs/data/workflowRunMutationInvalidation.ts:13,16` —
    `['workflow-runs','list']`, `['workflow-runs','key', …]`
  - `ui/src/modules/processed-calls/data/useReplayProcessedCallMutation.ts:29` — `['processed-calls','list']`
- **Fix:** route all three through `queryKeys` (add a no-filter list-prefix variant if the factory lacks one).
- **Acceptance:** no hand-written key arrays in those files; invalidation still works (tests for the three
  mutations assert the right query is invalidated); `npm run check` green.

---

## UAC-06 — `ui/AGENTS.md` module list is stale
- **Status:** ☑ Done (2026-06-18, Phase 1 — factual list refreshed; convention rules finalized in Phase 3)
- **Severity:** MEDIUM (doc drift in the canonical conventions file)
- **Evidence:** `ui/AGENTS.md:33-38` lists only `webhooks` + `processed-calls`. Actual modules (11): auth,
  dashboard, landing, persons, settings, webhooks, processed-calls, workflows, workflows-builder,
  workflow-runs. "Step 1 status snapshot" frozen at 2026-03-19.
- **Fix:** refresh the module-boundary + layer-rules sections; fold in the UAC-02 schema-ownership decision and
  the UAC-04 boundary rules; note the auth-transport exception.
- **Acceptance:** AGENTS.md reflects the real module set and the resolved conventions; no contradictions with
  `src/shared/ui/README.md`.

---

## UAC-07 — Misplaced hook: `useWorkflowDetailActions.ts` in `lib/`
- **Status:** ☑ Done (2026-06-18, Phase 1 — co-located at `WorkflowDetailPage/`)
- **Severity:** LOW
- **Rule violated:** `lib/` = pure helpers/transformers (no React). The file uses `useState`/`useCallback`.
- **Evidence:** `ui/src/modules/workflows/ui/WorkflowDetailPage/lib/useWorkflowDetailActions.ts`.
- **Fix:** move to `modules/workflows/data/` (or alongside the page as a hook); update imports.
- **Acceptance:** no React imports remain under any `lib/`; `npm run check` + knip green.

---

## UAC-08 — Cross-module type ownership blur
- **Status:** ☐ Open
- **Severity:** LOW
- **Evidence:** `ui/src/modules/dashboard/ui/DashboardPage.tsx:13` imports `WorkflowRunSummary` from
  `modules/workflows/lib/workflowSchemas`, though `workflow-runs` is the domain owner; run/summary types are
  split between `workflows` and `workflow-runs`.
- **Fix:** consolidate run types under `workflow-runs` (or `shared/types`) with a single owner; re-point
  consumers. Coordinate with UAC-02 (schemas may move in that pass).
- **Acceptance:** run/summary types have one home; dashboard imports from the owner; `npm run check` green.

---

## UAC-09 — Recipes hardcode user-facing `"Yes"/"No"`
- **Status:** ☑ Done (2026-06-18, Phase 1 — `uiText.common.yes/no`)
- **Severity:** LOW (i18n blocker; violates `uiText` centralization)
- **Evidence:** `ui/src/shared/ui/recipes/FieldRow.tsx:53` and `KeyValueList.tsx:42` emit literal `'Yes'/'No'`.
- **Fix:** add `uiText.common.yes/no`; reference them in both recipes.
- **Acceptance:** no user-facing string literals in those recipes; tests still pass; `npm run check` green.

---

## UAC-10 — `.join(' ')` instead of `cn()`
- **Status:** ☑ Done (2026-06-18, Phase 1)
- **Severity:** LOW
- **Evidence:** `ui/src/shared/ui/PanelNav.tsx:21-26` and `AppRail.tsx:43-48` build `className` via
  `.join(' ')`, losing `tailwind-merge` conflict de-dup.
- **Fix:** use `cn()` (`shared/lib/cn.ts`) in both.
- **Acceptance:** both use `cn()`; visual parity verified in preview; `npm run check` green.

---

## UAC-11 — Test coverage gaps
- **Status:** ☐ Open
- **Severity:** LOW (flat `src/test/` layout itself is fine at this scale — do NOT restructure)
- **Evidence:** no dedicated tests for `WorkflowRunDetailPage`, `PersonsPage`, `ProcessedCallsPage`; some
  `workflows-builder` storyboard interaction paths untested.
- **Fix:** backfill behavior tests for the named pages + key storyboard interactions.
- **Acceptance:** each named page has ≥1 behavior test; `npm run test` green.

---

## UAC-12 — `WorkflowsPage.tsx` ~366 LOC
- **Status:** ☐ Open
- **Severity:** LOW
- **Evidence:** `ui/src/modules/workflows/ui/WorkflowsPage.tsx` mixes fragmented filter `useState`s + column
  defs + table + create modal + shell registration.
- **Fix:** extract `useWorkflowsTableColumns` and a consolidated filter-state hook; keep the page as composition.
- **Acceptance:** page materially smaller; behavior unchanged (existing selection/filter tests still pass);
  `npm run check` green.

---

## Out of scope (recorded so they aren't re-litigated)
- Visual/UX fidelity vs the design system — separate effort.
- Runtime performance profiling — only the static bundle note (UAC-03) is in scope.
- Backend contract correctness — UI consumes HTTP contracts as-is by design.
- Flat `src/test/` directory layout — intentional; not a finding.
