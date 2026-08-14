# UI Architecture Conformance

> **Status:** ✅ **Complete — 13 / 13 findings resolved** (Phases 1–5). The `ui/` module is aligned with
> `ui/AGENTS.md` + RD-011, with the layering CI-enforced. UAC-13 was discovered during Phase 3 and fixed.
> **Goal:** Drive every finding from the [UI Architecture Audit (2026-06-18)](../../audits/ui-architecture-audit-2026-06-18.md)
> to closure so the `ui/` module is fully aligned with the rules in `ui/AGENTS.md`,
> `developer-rules.md`, and `src/shared/ui/README.md`.
> **Gate (every change):** `npm run check` green (lint + lint:tokens + deadcode + build + test); add/adjust
> tests for changed behavior; browser-verify observable UI.

This folder tracks the remediation of the architecture audit. Nothing here changes product behavior — it is
**maintainability / coherence** work. The audit found no correctness defects.

## Files (archived shape — 3 canonical docs)
- **README.md** (this file) — overview, status board, and the full per-finding registry (UAC-01 … UAC-13)
  with evidence/fix/acceptance, folded in from the former `tracker.md` on consolidation.
- **[plan.md](plan.md)** — the phased execution plan + the detailed per-phase plans (folded in from the former
  `phase-1…5-plan.md`).
- **[implementation-log.md](implementation-log.md)** — the dated phase narratives (what changed, decisions,
  verification).

## Master status board

Severity = maintainability/risk over ~6 months, not runtime breakage (there is none).

| ID | Finding | Severity | Phase | Status |
|----|---------|----------|-------|--------|
| **UAC-01** | No path aliases → 5–6 level `../` import chains (14 files under WorkflowDetailPage) | HIGH | P1 | ☑ Done |
| **UAC-02** | `platform/` imports schemas from `modules/` (8 sites); inconsistent schema ownership | HIGH | P2 | ☑ Done |
| **UAC-03** | No code-splitting — builder + `@dagrejs/dagre` in main bundle | MED | P4 | ☑ Done |
| **UAC-04** | No lint enforcement of module/layer boundaries | MED | P3 | ☑ Done |
| **UAC-05** | Hardcoded query keys bypass `queryKeys` factory (3 files) | MED | P1 | ☑ Done |
| **UAC-06** | `ui/AGENTS.md` module list stale (says 2 modules; there are 11) | MED | P1 | ☑ Done |
| **UAC-07** | Misplaced hook `useWorkflowDetailActions.ts` in `lib/` | LOW | P1 | ☑ Done |
| **UAC-08** | Cross-module type ownership blur (`WorkflowRunSummary`) | LOW | P2 | ☑ Done |
| **UAC-09** | Recipes hardcode `"Yes"/"No"` (bypass `uiText`) | LOW | P1 | ☑ Done |
| **UAC-10** | `.join(' ')` instead of `cn()` (PanelNav, AppRail) | LOW | P1 | ☑ Done |
| **UAC-11** | Test coverage gaps (PersonsPage + WorkflowsPage filters done; builder page deferred) | LOW | P5 | ☑ Done |
| **UAC-12** | `WorkflowsPage.tsx` ~366 LOC — extract column/filter hooks | LOW | P5 | ☑ Done |
| **UAC-13** | `shared/ui/AppRail` imports `@modules/auth` (shared not a leaf) | LOW | P3 | ☑ Done |

**Status legend:** ☐ Open · ◐ In progress · ☑ Done · ⊘ Won't fix. Full per-finding detail in the registry below.

## Repo-decisions (resolved)
- **[RD-011](../../repo-decisions/RD-011-ui-schema-ownership.md) — UI schema ownership** (drove UAC-02/08):
  Accepted — platform owns schemas (`z.infer` in `platform/contracts/`).
- **Module-boundary enforcement** (UAC-04): done in Phase 3 as ESLint `no-restricted-imports` layer zones — no
  separate RD needed; the rules are documented in `ui/AGENTS.md` and reference RD-011.

## Definition of done — met ✅
1. All 13 findings ☑ Done (UAC-11's builder-page test deferred with recorded rationale — now tracked as
   [known-issue #35](../../engineering-reference/known-issues.md)).
2. RD-011 written + Accepted; boundary enforcement shipped (no second RD required).
3. `ui/AGENTS.md` refreshed to the real module set + the enforced conventions.
4. `npm run check` green; no dead code (knip); token guard clean. (408 tests, solo run.)
5. Status board shows 13 / 13 — ✅ Complete.


---

## Findings — full registry (folded from tracker on consolidation)

---

### UAC-01 — No path aliases → deep relative-import chains
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

### UAC-02 — `platform/` imports schemas/logic from `modules/` (dependency inversion + inconsistency)
- **Status:** ☑ Done (2026-06-18, Phase 2 — RD-011 Option A, z.infer; schemas in platform/contracts/, no port→adapter dep)
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

### UAC-03 — No code-splitting; builder + dagre always bundled
- **Status:** ☑ Done (2026-06-18, Phase 4 — Option C: lazy builder route + lazy detail StoryboardTab + prefetch)
- **Severity:** MEDIUM (performance)
- **Evidence:** No `React.lazy()` anywhere; `ui/src/app/router.tsx` imports every page eagerly, including
  `WorkflowBuilderPage` which pulls `@dagrejs/dagre`.
- **Fix:** `React.lazy()` the builder route(s) (`workflows/new`, `workflows/:key/edit`) behind `Suspense` with
  the existing `LoadingState` fallback. Optionally a manualChunks split for dagre.
- **Acceptance:** builder + dagre absent from the initial chunk (verify via `vite build` output); view-only
  routes (dashboard/webhooks/calls) load without them; `npm run check` green; a test asserts the lazy route
  renders behind Suspense.
- **Measured result:** initial bundle 217 → 194.5 kB gzip (~22.5 kB / ~10% off graph-free routes); dagre lives
  in an on-demand `StoryboardViewer` chunk (~17 kB gzip) shared by the builder + detail tab. Prefetch on
  detail mount hides the flash on the (read-only) viewing path. Builder stays lazy — future-proofs the planned
  interactive builder (its editor code will land in the lazy chunk, not the initial bundle). One justified
  `react-refresh` disable on `router.tsx` (route table, not an HMR target).

---

### UAC-04 — No lint enforcement of module/layer boundaries
- **Status:** ☑ Done (2026-06-18, Phase 3 — built-in `no-restricted-imports` zones; all 5 smoke-tested)
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

### UAC-05 — Hardcoded query keys bypass the `queryKeys` factory
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

### UAC-06 — `ui/AGENTS.md` module list is stale
- **Status:** ☑ Done (2026-06-18, Phase 1 factual list + Phase 3 final — boundary rules documented as enforced)
- **Severity:** MEDIUM (doc drift in the canonical conventions file)
- **Evidence:** `ui/AGENTS.md:33-38` lists only `webhooks` + `processed-calls`. Actual modules (11): auth,
  dashboard, landing, persons, settings, webhooks, processed-calls, workflows, workflows-builder,
  workflow-runs. "Step 1 status snapshot" frozen at 2026-03-19.
- **Fix:** refresh the module-boundary + layer-rules sections; fold in the UAC-02 schema-ownership decision and
  the UAC-04 boundary rules; note the auth-transport exception.
- **Acceptance:** AGENTS.md reflects the real module set and the resolved conventions; no contradictions with
  `src/shared/ui/README.md`.

---

### UAC-07 — Misplaced hook: `useWorkflowDetailActions.ts` in `lib/`
- **Status:** ☑ Done (2026-06-18, Phase 1 — co-located at `WorkflowDetailPage/`)
- **Severity:** LOW
- **Rule violated:** `lib/` = pure helpers/transformers (no React). The file uses `useState`/`useCallback`.
- **Evidence:** `ui/src/modules/workflows/ui/WorkflowDetailPage/lib/useWorkflowDetailActions.ts`.
- **Fix:** move to `modules/workflows/data/` (or alongside the page as a hook); update imports.
- **Acceptance:** no React imports remain under any `lib/`; `npm run check` + knip green.

---

### UAC-08 — Cross-module type ownership blur
- **Status:** ☑ Done (2026-06-18, Phase 2 — run types now single-owned at platform/contracts/workflowSchemas)
- **Severity:** LOW
- **Evidence:** `ui/src/modules/dashboard/ui/DashboardPage.tsx:13` imports `WorkflowRunSummary` from
  `modules/workflows/lib/workflowSchemas`, though `workflow-runs` is the domain owner; run/summary types are
  split between `workflows` and `workflow-runs`.
- **Fix:** consolidate run types under `workflow-runs` (or `shared/types`) with a single owner; re-point
  consumers. Coordinate with UAC-02 (schemas may move in that pass).
- **Acceptance:** run/summary types have one home; dashboard imports from the owner; `npm run check` green.

---

### UAC-09 — Recipes hardcode user-facing `"Yes"/"No"`
- **Status:** ☑ Done (2026-06-18, Phase 1 — `uiText.common.yes/no`)
- **Severity:** LOW (i18n blocker; violates `uiText` centralization)
- **Evidence:** `ui/src/shared/ui/recipes/FieldRow.tsx:53` and `KeyValueList.tsx:42` emit literal `'Yes'/'No'`.
- **Fix:** add `uiText.common.yes/no`; reference them in both recipes.
- **Acceptance:** no user-facing string literals in those recipes; tests still pass; `npm run check` green.

---

### UAC-10 — `.join(' ')` instead of `cn()`
- **Status:** ☑ Done (2026-06-18, Phase 1)
- **Severity:** LOW
- **Evidence:** `ui/src/shared/ui/PanelNav.tsx:21-26` and `AppRail.tsx:43-48` build `className` via
  `.join(' ')`, losing `tailwind-merge` conflict de-dup.
- **Fix:** use `cn()` (`shared/lib/cn.ts`) in both.
- **Acceptance:** both use `cn()`; visual parity verified in preview; `npm run check` green.

---

### UAC-11 — Test coverage gaps
- **Status:** ☑ Done (2026-06-18, Phase 5 — audit list was stale; PersonsPage + WorkflowsPage-filters
  backfilled. **WorkflowBuilderPage page-test deferred:** mounting it hangs the Vitest forks worker
  (~580s, no code-level open handle found); it's a thin read-only viewer whose render logic is already
  covered by `workflows-builder-pure.test.ts` (23 tests) — revisit with the planned interactive builder.
  Now tracked as [known-issue #35](../../engineering-reference/known-issues.md).)
- **Severity:** LOW (flat `src/test/` layout itself is fine at this scale — do NOT restructure)
- **Evidence:** no dedicated tests for `WorkflowRunDetailPage`, `PersonsPage`, `ProcessedCallsPage`; some
  `workflows-builder` storyboard interaction paths untested.
- **Fix:** backfill behavior tests for the named pages + key storyboard interactions.
- **Acceptance:** each named page has ≥1 behavior test; `npm run test` green.

---

### UAC-12 — `WorkflowsPage.tsx` ~366 LOC
- **Status:** ☑ Done (2026-06-18, Phase 5 — extracted `workflowColumns` + `useWorkflowsFilters`; 366 → 299 LOC)
- **Severity:** LOW
- **Evidence:** `ui/src/modules/workflows/ui/WorkflowsPage.tsx` mixes fragmented filter `useState`s + column
  defs + table + create modal + shell registration.
- **Fix:** extract `useWorkflowsTableColumns` and a consolidated filter-state hook; keep the page as composition.
- **Acceptance:** page materially smaller; behavior unchanged (existing selection/filter tests still pass);
  `npm run check` green.

---

### UAC-13 — `shared/ui/AppRail.tsx` imports `@modules/auth` (shared not a leaf)
- **Status:** ☑ Done (2026-06-18, Phase 3 — discovered during boundary work; fixed)
- **Severity:** LOW (layering smell; discovered after the original audit)
- **Evidence:** `AppRail` (a `shared/ui` primitive) imported `@modules/auth/ui/LogoutButton` — the only
  `shared → modules` edge, blocking the `shared`-is-leaf invariant.
- **Fix:** inject the logout control as a prop — `AppRail` gains `logout?: ReactNode`; `app/AppShell.tsx`
  (which already imports `LogoutButton`) passes `<LogoutButton variant="rail" />`. `shared` is now a clean
  leaf, enforced by the Phase-3 `shared`-leaf lint zone. Behavior unchanged.

---

### Out of scope (recorded so they aren't re-litigated)
- Visual/UX fidelity vs the design system — separate effort.
- Runtime performance profiling — only the static bundle note (UAC-03) is in scope.
- Backend contract correctness — UI consumes HTTP contracts as-is by design.
- Flat `src/test/` directory layout — intentional; not a finding.
