# UI Architecture Conformance — Phased Plan

Execution plan grouping the 13 findings (full registry in [README.md](README.md)) into reviewable batches.
Each phase is independently reviewable, ends with `npm run check` green, and updates the status board in
[README.md](README.md).

**Ordering principle:** unblock/clarify first (aliases, docs), then the decision-gated structural work
(schema ownership → boundary lint), then optional performance + hygiene.

---

## Phase 1 — Low-risk hygiene + unblockers
**Findings:** UAC-01 (aliases), UAC-05 (query keys), UAC-06 (AGENTS.md), UAC-07 (misplaced hook),
UAC-09 (Yes/No), UAC-10 (cn()).

- These are mechanical, isolated, and don't depend on any decision.
- UAC-01 (aliases) lands first as its own commit so later phases use clean imports.
- UAC-06 (AGENTS.md) is updated again at the end of Phase 3 once conventions are locked — this pass just
  fixes the stale module list.
- **Done signal:** all six ☑; no imports with 4+ `../`; no hand-written query keys; no React under `lib/`;
  `npm run check` green.

## Phase 2 — Schema ownership (decision-gated)
**Findings:** UAC-02 (schema inversion), UAC-08 (run type ownership).

- **Blocked on:** RD — Schema ownership (Option A platform-owned vs Option B module-owned). Write + Accept
  the RD before touching code.
- Apply the chosen convention uniformly across workflow / workflow-run / settings AND webhooks / persons so
  there is one rule. Fold UAC-08 (consolidate run/summary types) into the same move.
- **Done signal:** one schema-ownership convention repo-wide; only the documented auth exception crosses
  `platform → modules`; RD Accepted; `npm run check` green.

## Phase 3 — Boundary enforcement (decision-gated, depends on Phase 2)
**Findings:** UAC-04 (lint zones), UAC-06 (AGENTS.md final refresh).

- **Blocked on:** RD — Module-boundary lint zones, and Phase 2 (the platform↔modules rule must reflect the
  chosen schema convention).
- Add `no-restricted-imports` / `no-restricted-paths` zones; add a smoke check proving a wrong import fails
  lint. Finalize AGENTS.md to match enforced reality.
- **Done signal:** lint rejects a deliberate cross-layer violation; AGENTS.md matches; `npm run check` green.

## Phase 4 — Performance (optional, confirm first)
**Findings:** UAC-03 (lazy-load builder).

- Confirm view-only load-time is a real concern before doing this.
- `React.lazy()` builder routes behind `Suspense`; verify dagre leaves the initial chunk.
- **Done signal:** builder/dagre absent from initial bundle; lazy route test; `npm run check` green.

## Phase 5 — Test backfill + decomposition
**Findings:** UAC-11 (coverage gaps), UAC-12 (WorkflowsPage size).

- Backfill behavior tests for run-detail / persons / processed-calls / storyboard interactions.
- Extract `useWorkflowsTableColumns` + filter-state hook from `WorkflowsPage`.
- **Done signal:** named pages each have a behavior test; WorkflowsPage materially smaller, behavior
  unchanged; `npm run check` green.

---

## Cross-phase rules
- **Per AGENTS.md test policy:** every behavior change adds/adjusts a test, runs it, and runs the full suite;
  lint + build must pass each cycle. Browser-verify observable UI via the preview.
- **No dead code:** knip must stay green (UAC-01 may orphan nothing, but moves in P2/P5 can — clean up).
- **Token discipline:** UAC-09/UAC-10 touch shared/ui — keep `lint:tokens` green.
- After each phase: tick the README status board + tracker cards, append to `implementation-log.md`.

## Non-goals
See the [README](README.md) findings registry ("Out of scope"). No product-behavior changes anywhere in this effort.

## Risks
- **UAC-01 large diff** — isolate as one commit; lean on `tsc` + tests.
- **Phase 2/3 decision stalls** — if RDs aren't decided, Phases 1, 4, 5 still proceed independently.
- **Schema move (P2) regressions** — adapters validate at the boundary; keep Zod parse sites identical, only
  relocate definitions.


---

# Per-phase plans (folded in on consolidation, 2026-06-18)

The detailed per-phase plans, moved verbatim from `phase-N-plan.md` when the feature was archived.


---

## Phase 1 — Low-risk hygiene + unblockers (detailed plan)

Covers **UAC-01, UAC-05, UAC-06, UAC-07, UAC-09, UAC-10**. None are decision-gated. No product behavior
changes. Each step is its own reviewable commit; `npm run check` must be green before the next.

> Per project rule (memory: *review before commit*): implement → present → wait for OK. Tests/build may run
> freely; only commits wait.

### Locked decisions (2026-06-18)
- **Hook home (UAC-07):** co-locate at `WorkflowDetailPage/useWorkflowDetailActions.ts` (out of `lib/`).
- **Alias set (UAC-01):** layered — `@app/* @platform/* @modules/* @shared/* @styles/*` (+ `@/*` root).
- **Sweep scope (UAC-01):** thorough — convert all cross-layer relatives (`modules → shared|platform|app|
  other-module`) to aliases repo-wide; keep intra-feature `./` and single `../`.

### Recommended commit order

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

### Step 1 — UAC-05: query keys through the factory

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

### Step 2 — UAC-07: move the misplaced hook

`WorkflowDetailPage/lib/useWorkflowDetailActions.ts` uses React hooks but sits in `lib/` (pure-only). It also
exports the `ValidationViewState` type consumed by `StoryboardTab/index.tsx` and `StoryboardTab/ValidationStrip.tsx`.

**Decided:** co-locate at `WorkflowDetailPage/useWorkflowDetailActions.ts` (drop the `lib/` subfolder for it;
`lib/parseWorkflowGraph.ts` stays — it's genuinely pure).

**Importers to update (`./lib/…` → `./…`, `../lib/…` → `../…`):**
`WorkflowDetailPage/index.tsx:34`, `StoryboardTab/index.tsx:22`, `StoryboardTab/ValidationStrip.tsx:12`.
**Acceptance:** no React imports remain under any `lib/`; knip + suite green.

---

### Step 3 — UAC-09: Yes/No from uiText

`uiText.common` already exists (`shared/constants/uiText.ts:541`). **Add** `yes: 'Yes'`, `no: 'No'`.
**Update** `shared/ui/recipes/FieldRow.tsx:53` and `KeyValueList.tsx:42` to
`value ? uiText.common.yes : uiText.common.no`.
**Tests:** existing `shared-ui-recipes-field-row` / `…-key-value-list` already assert `getByText('Yes'/'No')` —
they **pass unchanged** because the values stay "Yes"/"No", now sourced centrally.
**Acceptance:** no user-facing literals in those recipes; suite green.

---

### Step 4 — UAC-10: cn() for class composition

Replace `[ … ].join(' ')` with `cn( … )` (import from `shared/lib/cn`) in:
`shared/ui/PanelNav.tsx:21-26`, `shared/ui/AppRail.tsx:43-48`.
**Tests:** existing nav tests cover active/inactive; add an assertion only if needed.
**Verify:** preview — active/inactive nav states render identically (visual parity).
**Acceptance:** both use `cn()`; `lint:tokens` + suite green.

---

### Step 5 — UAC-06: refresh AGENTS.md module list

`ui/AGENTS.md:33-38` (and the mirrored list ~line 80) name only `webhooks` + `processed-calls`. Update to the
real 11: auth, dashboard, landing, persons, settings, webhooks, processed-calls, workflows, workflows-builder,
workflow-runs (+ note `workflows-builder`'s expanded layout: `model/ state/ surfaces/ observability/`).
**This pass = factual list only.** The convention edits (schema ownership, boundary rules) are re-touched in
Phase 3 once those decisions land. Leave a marker: "layer/boundary rules finalized in Phase 3 (UAC-04)."
**Acceptance:** module list matches reality; no contradiction with `src/shared/ui/README.md`.

---

### Step 6 — UAC-01: path aliases + deep-import conversion (mechanical, last)

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

### Phase-1 done signal
All six tracker cards ☑; README status board shows 6/12; `npm run check` green; preview boots clean;
implementation-log.md has an entry per step. Open decisions resolved: hook destination (Step 2), alias-set +
conversion-scope (Step 6).


---

## Phase 2 — Schema ownership (UAC-02 + UAC-08), Option A

Decision: **Option A — platform owns the validation boundary**, implemented the **`z.infer` (DRY) way**:
move each schema file (zod schema **+** its inferred type, single source of truth) from `modules/` into
`platform/`. Recorded as **RD-011**. No product behavior change.
Per review-before-commit: implement → present → wait for OK.

> **Final home (post self-review): `platform/contracts/`**, not `platform/adapters/http/`. The first cut put
> the schemas in `adapters/http/`, which made ports import from an adapter folder (port→adapter, inconsistent
> with every other port). They were relocated to a neutral `platform/contracts/` leaf. RD-011 and
> `implementation-log.md` (Phase 2 parts 1 + 2) are the canonical record; sections below reflect the final
> `contracts/` layout.

---

### Verification (exhaustive — all culprits)

Dependency inversion = **7 import sites / 5 platform files**, from **3 module files**:
- `platform/ports/workflowPort.ts:9`, `workflowRunPort.ts:5`, `settingsPort.ts:1` — **types**
- `platform/adapters/http/httpWorkflowAdapter.ts:1-8`, `httpWorkflowRunAdapter.ts:1-4` — **zod values**
- `platform/adapters/http/httpSettingsAdapter.ts:1,5` — **zod value + `projectSettingsConfig` logic**

Sanctioned exception (NOT touched): `httpJsonClient.ts:2`, `sseWebhookStreamAdapter.ts:5` import the auth token store.

Files to move are self-contained (import only `zod`):
`modules/workflows/lib/workflowSchemas.ts`, `modules/settings/lib/settingsSchemas.ts`,
`modules/settings/lib/settingsProjection.ts` (the projection imports types from `./settingsSchemas`, which
moves with it).

Consumer counts (basename grep, exhaustive):
- **`workflowSchemas`** — 24 importers (22 `import type`, 2 adapters import zod values).
- **`settingsSchemas`** — 9 importers (7 `import type`, settings adapter + `settings-projection.test` import the zod value).
- **`settingsProjection`** — 2 importers (settings adapter + its test).

---

### Approach — move the files, keep `z.infer`

No type duplication, no hand-written `shared/types`. Each schema file stays as-is (schema + `z.infer` type in
one place) and relocates into `platform/`. The inversion is fixed because the files now **live in** platform;
consumers import from the new location. Direction becomes correct: `modules → platform`.

**Home by consumer breadth (two buckets):**
- App-wide contracts (imported by ports + modules + adapters) → **`platform/contracts/`** — a neutral leaf
  everyone can depend on without inverting a layer.
- Adapter-internal schemas (imported only by their adapter) → stay in **`platform/adapters/http/`**.

```
platform/contracts/
  workflowSchemas.ts          ← from modules/workflows/lib/   (zod + z.infer types, unchanged content)
  settingsSchemas.ts          ← from modules/settings/lib/

platform/adapters/http/
  personSchemas.ts            (stays — adapter-internal)
  webhookSchemas.ts           (stays — adapter-internal)
  processedCallSchemas.ts     (stays — adapter-internal)
  settingsProjection.ts       ← from modules/settings/lib/    (transform logic, not a shape;
                                  imports types from ../../contracts/settingsSchemas)
```

**Accepted trade-off:** the *type-source mechanism* isn't uniform across domains — workflow/settings expose
types via `z.infer` from `platform/contracts/*`; person/webhook hand-write theirs in `shared/types`. Both are
platform-owned and depended on inward; not worth hand-duplicating workflow/settings types to "normalize."

---

### Repoint map (mechanical — mostly `@modules/...` → `@platform/contracts/...`)

**Adapters (in `platform/adapters/http/`) → relative `../../contracts/`**
- `httpWorkflowAdapter.ts`, `httpWorkflowRunAdapter.ts` → `../../contracts/workflowSchemas`
- `httpSettingsAdapter.ts` → `../../contracts/settingsSchemas` (schema) + `./settingsProjection` (logic, local)
- `settingsProjection.ts` → `../../contracts/settingsSchemas` (types)

**Ports (in `platform/ports/`) → relative `../contracts/`** (port → contract, **not** port → adapter)
- `workflowPort.ts`, `workflowRunPort.ts` → `../contracts/workflowSchemas` (types)
- `settingsPort.ts` → `../contracts/settingsSchemas` (type)

**Feature modules + tests (type imports) → `@platform/contracts/...`**
- workflow types (workflows/workflow-runs/dashboard + 2 tests) → `@platform/contracts/workflowSchemas`
- settings types (`SettingRow`, `settingsSections`, `SettingsPage`, `settings-hooks.test`, `settings-page.test`)
  → `@platform/contracts/settingsSchemas`
- `settings-projection.test` → `@platform/contracts/settingsSchemas` + `@platform/adapters/http/settingsProjection`

**UAC-08 resolved:** `WorkflowRunSummary` & friends now have one owner (`platform/contracts/workflowSchemas`);
`dashboard`/`workflow-runs` import from there, not from the `workflows` module. The cross-module borrow is gone.

---

### Step sequence (each reviewable; `npm run check` green between)

1. **RD-011** — write `Docs/repo-decisions/RD-011-ui-schema-ownership.md` (Accepted: Option A, z.infer; two
   homes — `platform/contracts/` for app-wide contracts, `platform/adapters/http/` for adapter-internal;
   auth token import noted as the standing exception). Add to the RD index.
2. **Move files** — `git mv` `workflowSchemas.ts` + `settingsSchemas.ts` → `platform/contracts/`;
   `settingsProjection.ts` → `platform/adapters/http/` (history preserved).
3. **Repoint adapters + ports** — adapters → `../../contracts/*`; ports → `../contracts/*`.
4. **Repoint modules + tests** — type importers + projection test (per map above).
5. **Verify deletion is clean** — old module paths gone; `knip` shows no orphaned files/exports.
6. **Docs** — tick UAC-02 + UAC-08 in `tracker.md` / `README.md`; append to `implementation-log.md`.

No new test file is required (DRY — there is no second type definition to keep in sync). Existing adapter +
hook + page tests already exercise these schemas/types and must stay green.

---

### Risks
- **Missed importer / stale path** → exhaustive basename grep done; `tsc -b` fails on any stale import, so
  nothing can silently slip.
- **`git mv` history** → use `git mv` so blame survives.
- **Scope creep** → do NOT split `workflowSchemas` into workflow vs workflow-run files; move as-is.

### Acceptance
- `grep` of `src/platform` shows **zero** `@modules/` imports except the documented auth exception.
- No port imports from `adapters/http`; schemas are platform-owned (contracts/ + adapters/http by breadth).
- `WorkflowRunSummary` et al. have a single owner; no cross-module type borrow remains.
- RD-011 Accepted; `npm run check` green (396 tests).

### Non-goals
- No change to webhook/person/processedCall (already platform-owned).
- Not converting person/webhook to `z.infer` (out of scope; their hand-written `shared/types` split stays).
- Phase 3 (boundary-lint enforcement, UAC-04) consumes this outcome but is separate.


---

## Phase 3 — Boundary enforcement (UAC-04) + finalize AGENTS (UAC-06)

Goal: make the layering invariants Phases 1–2 established **unbreakable in CI**, so they can't silently
regress. One small source refactor (AppRail) to make `shared/` a true leaf; otherwise no behavior change.
Per review-before-commit: implement → present → wait for OK.

### Locked decisions (2026-06-18)
- **Mechanism:** built-in `no-restricted-imports` (zero new deps; matches our aliased specifiers).
- **Contracts guard:** yes — `platform/contracts/` restricted to a zod-only leaf.
- **AppRail smell (UAC-13):** **fix in this phase** (not deferred) → make `shared/` a clean leaf and add a
  `shared`-is-leaf lint zone.

---

### Verification (the rules already hold — this is a guardrail, not a fix)
- `platform → @modules`: only `httpJsonClient.ts` + `sseWebhookStreamAdapter.ts` (the sanctioned auth-token import).
- `ports → adapters`: **none**.
- `platform/contracts/`: imports **only `zod`** (clean leaf).
So adding the rules should yield **0 new lint errors** on first run. If any fire, that's a real find to fix.

### Approach — built-in `no-restricted-imports`, flat-config zones (no new dependency)

`eslint-plugin-import`'s `no-restricted-paths` is purpose-built but needs the plugin **and** a TS-alias
resolver (`eslint-import-resolver-typescript`) + config. Because Phase 1 made every cross-layer import use an
**alias** (`@modules/…`, `@platform/…`), the built-in rule can match those specifier strings directly — no
resolver, no new deps. The auth exception is expressed with flat-config `ignores`.

**Decision to confirm:** built-in `no-restricted-imports` (recommended — zero deps) vs `eslint-plugin-import`
`no-restricted-paths` (purpose-built, +2 deps + resolver config). Plan below assumes built-in.

#### Config shape (`ui/eslint.config.js`)
Extract reusable pattern objects, compose per-zone (flat config overrides the whole rule per file, so each
zone restates the patterns that apply to it):

```js
const NO_DEEP_RELATIVE = { group: ['../../../../**'], message: 'Use a path alias (@app/@platform/@modules/@shared).' }
const NO_MODULES = { group: ['@modules/**'], message: 'platform must not import from modules (RD-011). Auth token store in httpJsonClient/sseWebhookStreamAdapter is the sole exception.' }
const NO_ADAPTERS = { group: ['../adapters/**', '@platform/adapters/**'], message: 'ports must not import from adapters (RD-011). Import contract types from @platform/contracts.' }
```

Config objects (added after the existing global block):
1. **global** (existing, unchanged): `patterns: [NO_DEEP_RELATIVE]` — applies everywhere.
2. **`files: ['src/platform/**/*.{ts,tsx}']`, `ignores: [httpJsonClient.ts, sseWebhookStreamAdapter.ts]`** →
   `patterns: [NO_DEEP_RELATIVE, NO_MODULES]`. (The 2 ignored files fall back to the global depth-only rule —
   that's the auth carve-out.)
3. **`files: ['src/platform/ports/**/*.{ts,tsx}']`** → `patterns: [NO_DEEP_RELATIVE, NO_MODULES, NO_ADAPTERS]`.
   (Ports are platform, so they also get NO_MODULES; ports block is more specific so it restates all three.)
4. **`files: ['src/platform/contracts/**/*.{ts,tsx}']`** →
   `patterns: [NO_DEEP_RELATIVE, { group: ['@app/**','@modules/**','@shared/**','@platform/**'], message: 'contracts is a leaf — import only zod.' }]`. Keeps contracts from accreting dependencies. Passes today.
5. **`files: ['src/shared/**/*.{ts,tsx}']`** →
   `patterns: [NO_DEEP_RELATIVE, { group: ['@app/**','@modules/**','@platform/**'], message: 'shared is a leaf — must not import from app/modules/platform.' }]`. Passes **after** the AppRail fix (step 1). `@shared`
   is not banned (intra-shared is fine).

`container.ts` legitimately imports adapters (it wires them) — not a port, so unaffected. `modules → @app`
stays allowed (shell hooks). Cross-module imports stay allowed (not an RD-011 invariant; out of scope).

### Step 1 — Fix AppRail so `shared/` is a leaf (UAC-13)
`shared/ui/AppRail.tsx` is the **only** `shared → modules/app/platform` edge: it imports
`@modules/auth/ui/LogoutButton`. AppRail is rendered **only** by `app/AppShell.tsx`, which **already imports
`LogoutButton`**. So inject it via a prop (app layer composes; shared primitive stays generic):
- `AppRail.tsx`: add prop `logout?: ReactNode`; drop the `@modules/auth` import; render `{logout}` in the
  footer in place of `<LogoutButton variant="rail" />` (keep `ThemeToggle` — it's `shared`).
- `AppShell.tsx`: `<AppRail logout={<LogoutButton variant="rail" />} />` (LogoutButton already imported here).
- Behavior unchanged — same component renders in the same slot. No AppRail unit test exists; shell tests
  render `AppShell` and should stay green (identical output).

### Steps 2+ (each reviewable; `npm run check` green between)
2. Edit `ui/eslint.config.js`: add the const pattern objects + zones 2–5 above (incl. `shared`-is-leaf).
3. Run `npm run lint` — expect **0 errors** (invariants hold after step 1). If any fire, fix or document.
4. **Smoke-test each zone** (the review dinged the depth rule for matching nothing): temporarily add an
   illegal import (platform → `@modules/workflows/...`; a port → `../adapters/http/...`; a contracts file →
   `@shared/...`; a shared file → `@modules/...`), confirm `eslint` errors with the right message, then
   revert. Record the smoke evidence in the implementation log.
5. **Finalize AGENTS.md (UAC-06 carryover):** remove the Phase-3 "to-be-finalized" marker; state the boundary
   rules are now lint-enforced (platform∌modules + auth exception; ports∌adapters; contracts/shared leaves).
6. Docs: tick UAC-04 (+ UAC-06 final) and add **UAC-13** (AppRail/shared-leaf, done) in `tracker.md` /
   `README.md`; append to `implementation-log.md`.

No new test file (ESLint rules aren't unit-tested here; step 4's smoke test is the done-signal). The AppRail
change is covered by existing shell tests. `npm run check` must stay green.

### Risks
- **Flat-config rule override** — a per-zone `no-restricted-imports` replaces (doesn't merge) the global one
  for matched files, so each zone restates `NO_DEEP_RELATIVE`. Mitigated by the shared const.
- **Glob/`ignores` correctness** — verify the auth carve-out works (those 2 files may import `@modules/auth`)
  and that ports glob is a subset handled by step 3's smoke test.
- **`**` matching** — patterns use `@modules/**` (not `@modules/*`) so nested paths match (minimatch `*`
  doesn't cross `/`).

### Acceptance
- AppRail no longer imports `@modules`; `grep` shows **zero** `shared → @app/@modules/@platform` edges.
- `npm run lint` green with all 5 zones in place; `npm run check` green.
- Smoke test shows each zone errors on a deliberate violation (evidence in the log).
- AGENTS.md no longer says "finalized in Phase 3"; boundary rules documented as enforced.
- UAC-04, UAC-06 (final), UAC-13 ticked. Tracker/README updated.

### Non-goals
- Banning cross-module imports (not an RD-011 invariant; separate policy if ever wanted).
- Adding `eslint-plugin-import`/boundaries plugin (built-in suffices for our aliased imports).
- Relocating other shell chrome (AppPanel/PanelNav/InspectorPanel stay in `shared/ui`; they don't import modules).


---

## Phase 4 — Code-split the workflow graph / dagre (UAC-03)

Goal: get `@dagrejs/dagre` (and the storyboard render surface) out of the **initial** JS bundle so pages that
never show a workflow graph (dashboard, webhooks, calls, persons, settings, runs, workflow *list*) don't
download it. Performance-only; no behavior change. Per review-before-commit: implement → present → wait.

> **This is optional.** Do it only if initial load for graph-free users is worth a small amount of lazy-load
> machinery + a possible loading flash on the workflow-detail page. The "measure first" step below de-risks the
> decision with real numbers before any test churn.

---

### What actually pulls in dagre (the load-bearing finding)

`@dagrejs/dagre` is imported by **exactly one file**: `workflows-builder/model/layoutEngine.ts`. The only
**runtime** path to it is `layoutStoryboard` ← `useStoryboardModel` (a value import). Every other reference to
`layoutEngine` is `import type { SceneLayout/StoryboardLayout }` — **erased at build, no dagre**.

`useStoryboardModel` (→ dagre) is reached from **two eager routes**:
1. **Builder** (`workflows/new`, `workflows/:key/edit`) → `WorkflowBuilderPage` → `surfaces/storyboard/Storyboard`.
2. **Workflow detail** (`workflows/:key`) → `WorkflowDetailPage` → `StoryboardTab` (the **default** tab).

So lazy-loading the builder route **alone would NOT remove dagre** — the detail page's storyboard tab still
pulls it into main. **Both** static chains must be cut.

`surfaces/storyboard/accentTokens` is imported by `WorkflowStepTimeline` (eager, runs page) and `InspectorBody`,
but it's a pure color-token module that does **not** import `layoutEngine`/dagre — so it stays in the main
bundle harmlessly. (Pre-flight: re-confirm `accentTokens` has no heavy imports.)

Both heavy components are **single-importer**: `Storyboard.tsx` only by the builder page; `StoryboardTab` only
by `WorkflowDetailPage/index.tsx`. Clean cut points.

---

### Step 0 — Measure first (decide on real numbers)
`npm run build` today: single `index-*.js` = **730 kB / 217 kB gzip** (no splitting). Add a throwaway
`build.rollupOptions`/`manualChunks` or just `lazy()` the two boundaries on a scratch branch and re-build to see
the dagre/storyboard chunk size. Estimate ~**30–45 kB gzip** for dagre + a bit for the surface — i.e. a ~15–20%
initial-JS cut for graph-free users. **If the measured win is too small to justify the flash (below), stop here
and mark UAC-03 won't-fix.**

### The central decision (pick before implementing)
| Option | What | Cost |
|---|---|---|
| **A — full split (recommended)** | lazy the builder route **and** the detail `StoryboardTab` | dagre leaves main; brief loading flash on the detail page's default tab (first visit, then cached) |
| **B — builder-only** | lazy only the builder route | no flash on common pages, but **dagre stays in main** (detail tab uses it) → small win (splits builder chrome/`builderStore`/`debugOverlay`, not dagre) |
| **C — A + prefetch** | A, plus prefetch the storyboard chunk on workflows-list/detail mount or row hover | near-zero flash; a little more code |

Recommendation: **A**, optionally **C** if the detail-page flash is objectionable. B doesn't achieve the
finding's goal (dagre is the heavy bit and it's in the detail tab).

---

### Implementation (Option A)
1. **Lazy the builder route** — `app/router.tsx`:
   ```ts
   const WorkflowBuilderPage = lazy(() =>
     import('@modules/workflows-builder/ui/WorkflowBuilderPage').then((m) => ({ default: m.WorkflowBuilderPage })))
   ```
   (named-export → default mapping). Wrap both builder route elements:
   `element: <Suspense fallback={<LoadingState />}><WorkflowBuilderPage /></Suspense>`.
2. **Lazy the StoryboardTab** — `WorkflowDetailPage/index.tsx`:
   ```ts
   const StoryboardTab = lazy(() => import('./StoryboardTab').then((m) => ({ default: m.StoryboardTab })))
   ```
   Wrap the storyboard branch: `<Suspense fallback={<LoadingState />}><StoryboardTab … /></Suspense>`.
   (`RunsTab` stays eager — no dagre.)
3. **Vite auto-chunks.** React.lazy dynamic imports create async chunks; Vite hoists the storyboard surface +
   `layoutEngine` + dagre into a **shared** async chunk used by both boundaries. No `manualChunks` required
   (optional: name it via `build.rollupOptions.output.manualChunks` for a readable `storyboard-*.js`).
4. **(Option C only)** add a `import(...)`-on-idle prefetch (e.g. in `WorkflowsPage` mount or a row `onMouseEnter`).

`LoadingState` already exists and is the standard fallback — reuse it (no new component).

### Test impact — small (most tests untouched)
- **Storyboard/surface unit tests** (`workflow-storyboard-*`, `workflows-builder-pure`, `SceneInspectorPopover`,
  `ValidationStrip`, etc.) import the modules **directly** → **unaffected** by route/tab lazy-loading.
- **`workflow-detail-page-actions.test.tsx`** is the only full `<WorkflowDetailPage>` render. It **already uses
  `await screen.findByText('Validation In Progress')`** (storyboard content) — async, so it tolerates the lazy
  tab + Suspense. **Verify it passes**; no rewrite expected.
- **No test renders the builder route** through the router (`app-routing.test` doesn't navigate there) → builder
  lazy-load has ~zero test impact.
- **Add one focused test** (UAC-03 acceptance): assert the detail page shows content after the storyboard tab
  lazily resolves (the existing actions test largely covers this — extend or add a minimal Suspense-resolves
  assertion rather than a brittle "fallback is visible" check).

### Risks
- **Loading flash on the default detail tab** (Option A) — the most-common workflow view gains a brief fallback
  on first visit until the chunk loads (then cached). Mitigate with Option C prefetch if it matters.
- **Suspense in the router** — `createBrowserRouter` route `element`s wrapped in `<Suspense>` is supported;
  alternatively use React Router v7's route-level `lazy` API (cleaner, but restructures route defs). Plan uses
  `React.lazy + Suspense` (smaller diff).
- **Named-export mapping** — `lazy()` needs a default; use the `.then((m) => ({ default: m.X }))` form.
- **jsdom dynamic import** — resolves fast in tests; `findBy*` handles the async. Watch for any test using
  synchronous `getBy*` on storyboard content (none found).

### Acceptance
- `npm run build`: dagre + storyboard surface in a **separate async chunk**, absent from the initial `index-*.js`;
  main chunk shrinks by the measured amount. Graph-free routes load without the storyboard chunk (verify via
  the build's chunk list / network in preview).
- `npm run check` green (396+ tests); the full-page detail test passes with the lazy tab.
- UAC-03 ticked; README/tracker/log updated.

### Non-goals
- General vendor chunking (React/router/query/zod/radix/lucide splitting) — separate optimization, not UAC-03.
- Lazy-loading other routes (no heavy deps; not worth the flash).
- Changing the storyboard/builder behavior or the dagre layout itself.


---

## Phase 5 (final) — Test backfill (UAC-11) + decompose WorkflowsPage (UAC-12)

Goal: close the last two findings → **13/13**. Two synergistic LOW-severity items: decomposing `WorkflowsPage`
(UAC-12) produces directly-testable units that also serve the coverage backfill (UAC-11). No behavior change.
Per review-before-commit: implement → present → wait.

---

### Correction: the audit's UAC-11 list was stale
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

### UAC-12 — decompose `WorkflowsPage` (366 LOC)

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

### UAC-11 — backfill the real gaps

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

### Order of work (each step `npm run check` green)
1. **Add `workflows-page-filters.test`** (characterize current filter behavior).
2. **UAC-12 extract** `workflowColumns` + `useWorkflowsFilters`; rewire WorkflowsPage; existing selection test +
   the new filters test stay green (proves behavior-preserving).
3. **Add `useWorkflowsFilters` unit test.**
4. **Add `persons-page.test`** (gap).
5. **Add `workflow-builder-page.test`** (gap).
6. Docs: tick UAC-11 + UAC-12; flip README to ✅ 13/13 Complete; append implementation-log; refresh the
   project memory.

### Risks
- **Refactor regressions** → mitigated by characterization test (step 1) + the existing selection test; the
  decomposition is pure extraction (no logic change).
- **Filter draft-keying subtlety** — the `draftFilterState.key === filterDraftKey` reconciliation (resets the
  draft when the *applied* status changes out from under it) is the one non-obvious bit; the hook must
  preserve it exactly. The unit test (step 3) pins it.
- **PersonsPage test breadth** — keep it behavior-focused (render/filter/navigate/paginate/states), not
  snapshot; mirror the existing list-page tests' style.
- **Test count** — adds ~4 test files; `npm run check` stays the gate.

### Acceptance
- `WorkflowsPage` materially smaller; `workflowColumns` + `useWorkflowsFilters` extracted and (filters) unit-tested.
- `PersonsPage` + `WorkflowBuilderPage` have dedicated behavior tests; `WorkflowsPage` filter apply/reset covered.
- `npm run check` green (≥396 + new tests). Behavior unchanged.
- UAC-11 + UAC-12 ticked → **13/13**; README flips to ✅ Complete; memory updated.

### Non-goals
- A shared filterable-list abstraction across WorkflowsPage/PersonsPage (separate refactor).
- Heavy WorkflowBuilderPage tests (defer to the planned interactive builder).
- Moving already-clean local helpers (`CatalogSection`, etc.) to separate files.
- Raising overall coverage targets / adding e2e — out of scope.
