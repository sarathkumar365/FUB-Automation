# UI Architecture Conformance — Implementation Log

Append one entry per finding (or phase) as it lands. Newest at top. Keep it factual: what changed, files
touched, tests added, verification evidence. Mirrors the `ui-design-system-conformance` log convention.

Format per entry:

```
## UAC-NN — <title>  (YYYY-MM-DD)
### Changes
- <file> — <what / why>
### Tests
- <test file> — <what it asserts>
### Validation
- `npm run check` green (N tests). [browser-verified: ...]
### Notes / decisions
- <any RD impact or deviation>
```

---

## Phase 3 code-review fixes — boundary-lint hardening (root-cause)  (2026-06-18)

A fresh-eyes review found the boundary guard was *spelling-precise*, not *path-precise*: it caught the
aliased import (`@modules/**`) but not a relative-spelled one (`../../../modules/...`, under the 4+ depth ban),
and the auth exception was a file-wide `ignores` (broader than "token store only"). Both share one root cause
— built-in `no-restricted-imports` matches the import *string* and can only exempt a whole file. Fixed at root
without new deps:

- **#1 (relative-spelling bypass):** each banned boundary now lists BOTH spellings — alias (`@modules/**`) and
  relative (`**/modules/**`) — the only two ways to spell a static import into a directory. Done for
  NO_MODULES, NO_ADAPTERS, CONTRACTS_LEAF, SHARED_LEAF.
- **#2 (over-broad exception):** removed the platform zone's file-wide `ignores`; added a per-line
  `// eslint-disable-next-line no-restricted-imports` at the exact auth-token import in `httpJsonClient.ts` +
  `sseWebhookStreamAdapter.ts`. Exception now scoped to that one import; those files are otherwise fully
  governed; the brittle path list is gone from the config.

### Validation
- Re-smoke-tested the closed gaps: a relative `../../../modules/...` import now errors; a non-auth `@modules`
  import added to `httpJsonClient.ts` now errors (file no longer blanket-exempt); the real auth import lints
  clean via the per-line disable.
- `npm run check` green — **396 tests**. No false positives from the `**/<layer>/**` patterns on current code.

---

## Phase 3 — UAC-04 boundary lint + UAC-06 final + UAC-13 (AppRail/shared-leaf)  (2026-06-18)

Converts the layering invariants from "true by discipline" to "enforced by CI." Built-in
`no-restricted-imports` (no new deps). One source refactor (AppRail) to make `shared/` a true leaf.

### Changes
- **UAC-13 — AppRail fix:** `shared/ui/AppRail.tsx` no longer imports `@modules/auth/ui/LogoutButton`. Added
  prop `logout?: ReactNode`; `app/AppShell.tsx` (already imports `LogoutButton`) passes
  `<LogoutButton variant="rail" />`. `shared` is now a clean leaf. Behavior unchanged (same control, same slot).
- **UAC-04 — lint zones** (`eslint.config.js`): shared pattern consts + 5 zones —
  global (depth); `platform/**` minus the 2 auth files (+ no `@modules`); `platform/ports/**`
  (+ no adapters); `platform/contracts/**` (zod-only leaf); `shared/**` (no app/modules/platform).
- **UAC-06 final:** `ui/AGENTS.md` — removed the "finalized in Phase 3" marker; documented the enforced
  boundaries (platform∌modules + auth exception; ports∌adapters; contracts/shared leaves; 4+ relative ban).

### Validation
- `npm run lint` green (**0 errors** — invariants already held after the AppRail fix).
- **Smoke-tested all 5 zones:** temporary illegal imports each errored with the right message
  (platform→@modules, ports→../adapters, contracts→@shared, shared→@modules); the ignored auth file
  (`httpJsonClient`) lints clean. Probes removed.
- `npm run check` green — **396 tests** (AppRail change covered by existing shell tests).

### Notes / decisions
- Built-in `no-restricted-imports` over `eslint-plugin-import` (zero deps; matches aliased specifiers).
- Did NOT ban cross-module imports or `modules → @app` (not RD-011 invariants; the latter is legitimate
  shell-hook usage). Other shell chrome (AppPanel/PanelNav/InspectorPanel) already import no modules — untouched.

---

## Code-review fixes (fresh-eyes review of the branch)  (2026-06-18)

A 7-angle code review found **no correctness bugs** (tsc 0, 396 tests, aliases resolve, queryKeys
byte-identical, hook move correct, cn() no behavior change). Two cleanup items fixed:

- **Hardcoded query key the UAC-05 migration missed** — `modules/workflow-runs/data/useWorkflowRunsForKeyQuery.ts:18`
  had a `['workflow-runs','key','none',filters]` fallback bypassing the factory. Replaced with
  `queryKeys.workflowRuns.listForKey(key || 'none', filters)` — byte-identical (used `||`, not `??`, to
  preserve the empty-string-falsy behavior of the old `key ?` ternary). UAC-05 originally fixed 3 files;
  this was a 4th site it didn't catch.
- **Dead aliases removed** — `@/*` and `@styles/*` had zero importers (`styles/` is CSS-only). Dropped from
  `tsconfig.app.json` + `vite.config.ts`; updated the eslint message, and AGENTS.md (alias list + pre-flight)
  to the 4 live aliases `@app/@platform/@modules/@shared`, with the rationale (one alias per src dir holding
  JS/TS modules; styles is CSS-only).

`npm run check` green (396 tests). No behavior change.

---

## Phase 2 (part 2) — relocate shared schemas to `platform/contracts/`  (2026-06-18)

Brutal self-eval of part 1 found a real wrinkle: putting the schemas in `platform/adapters/http/` made the 3
ports import from `adapters/http` — a port→adapter dependency that **no other port has** (the 4 existing ports
source types from `shared/types` or inline). Root cause: the existing `adapters/http` schemas are
adapter-internal (imported only by their adapter), whereas workflow/settings schemas are imported across
ports + modules + adapters. Co-locating them was a category error.

### Changes
- **`git mv`** `workflowSchemas.ts` + `settingsSchemas.ts` → new `platform/contracts/` (history preserved).
  `settingsProjection.ts` **stays** in `adapters/http/` (it's transform logic, not a shape) and now imports
  its types from `../../contracts/settingsSchemas`.
- **Repointed 33 imports** (one-shot codemod, deleted): adapters → `../../contracts/*`; ports →
  `../contracts/*` (now port→contract, not port→adapter); modules + tests → `@platform/contracts/*`
  (resolves via the existing `@platform/*` alias — no new alias needed).
- person/webhook/processedCall schemas **untouched** in `adapters/http/` — correctly adapter-internal.
- **RD-011 updated** to the two-home model (contracts/ for app-wide contracts, adapters/http for
  adapter-internal) with the rationale.

### Validation
- `grep`: ports no longer import from `adapters/http` at all; zero `@modules/` in platform except auth.
- `npm run check` green — **396 tests**.

---

## Phase 2 (part 1) — UAC-02 + UAC-08 (schema ownership, RD-011 Option A)  (2026-06-18)

Platform now owns the API validation boundary. `z.infer` (single-source) style kept — schema files moved, not
duplicated.

### Changes
- **RD-011** — new `Docs/repo-decisions/RD-011-ui-schema-ownership.md` (Accepted: Option A, z.infer, home =
  `platform/adapters/http/`; auth token import noted as standing exception). Added to RD index.
- **Moved (git mv, history preserved)** into `platform/adapters/http/`:
  `workflowSchemas.ts` (was `modules/workflows/lib/`), `settingsSchemas.ts` + `settingsProjection.ts`
  (were `modules/settings/lib/`). Content unchanged (schema + `z.infer` type together).
- **Repointed 34 imports** across ~29 files (one-shot codemod, since deleted):
  - adapters (`httpWorkflowAdapter`, `httpWorkflowRunAdapter`, `httpSettingsAdapter`) → relative `./*`
  - ports (`workflowPort`, `workflowRunPort`, `settingsPort`) → relative `../adapters/http/*`
    (intra-platform convention, matching how adapters already import ports)
  - feature modules + tests → `@platform/adapters/http/*`
- **UAC-08:** `WorkflowRunSummary` & run types now single-owned at `platform/adapters/http/workflowSchemas`;
  the dashboard's cross-module borrow from the `workflows` module is gone.

### Tests
- No new test needed (DRY — one `z.infer` definition, nothing to keep in sync). Existing adapter/hook/page
  tests already exercise these schemas+types and stay green (the queryKeys/run tests were repointed in P1).

### Validation
- `grep`: zero `@modules/` imports inside `src/platform` except the documented auth exception
  (`httpJsonClient`, `sseWebhookStreamAdapter`). All 5 HTTP schema files now in `platform/adapters/http/`.
- `npm run check` green — lint + lint:tokens + knip + build + **396 tests**.

### Notes / decisions
- RD-011 records the owner choice of `z.infer` over the hand-written `shared/types` split (no duplication).
- Accepted cosmetic wrinkle: modules import workflow/settings *types* from an `adapters/http` path (person/
  webhook keep theirs in `shared/types`). Allowed (`modules → platform`); not worth duplicating types to fix.
- Did NOT split `workflowSchemas` into workflow vs workflow-run files (out of scope).

---

## Phase 1 (part 1) — UAC-05, 06, 07, 09, 10  (2026-06-18)

The five small, non-decision-gated fixes. UAC-01 (alias sweep) lands separately next.

### Changes
- **UAC-05** — `platform/query/queryKeys.ts`: added list-prefix helpers `workflows.lists()`,
  `workflowRuns.lists()`, `workflowRuns.forKey(key)`, `processedCalls.lists()`. Swapped the 3 hand-written
  key arrays in `workflowMutationInvalidation.ts`, `workflowRunMutationInvalidation.ts`,
  `useReplayProcessedCallMutation.ts` (added the missing `queryKeys` import to the last). Prefixes preserve
  the original TanStack prefix-match invalidation semantics.
- **UAC-07** — `git mv` `WorkflowDetailPage/lib/useWorkflowDetailActions.ts` →
  `WorkflowDetailPage/useWorkflowDetailActions.ts` (it's a hook, not pure-`lib`). Fixed its own relative
  imports (one level shallower) and 3 importers (`index.tsx`, `StoryboardTab/index.tsx`,
  `StoryboardTab/ValidationStrip.tsx`). `lib/parseWorkflowGraph.ts` stays — genuinely pure.
- **UAC-09** — `shared/constants/uiText.ts`: added `common.yes/no`. `recipes/FieldRow.tsx` +
  `recipes/KeyValueList.tsx` now source them instead of literal `'Yes'/'No'`.
- **UAC-10** — `shared/ui/AppRail.tsx` + `shared/ui/PanelNav.tsx`: `cn()` instead of `[...].join(' ')`.
- **UAC-06** — `ui/AGENTS.md`: module list refreshed to the real 11 modules + layer split +
  `workflows-builder` expansion; added the missing `modules/*/lib` rule; left a Phase-3 marker for the
  schema-ownership / boundary conventions (UAC-02/UAC-04).

### Tests
- `src/test/query-keys.test.ts` — new (2 cases): list-prefix helpers are a strict prefix of their filtered
  counterparts; `forKey` is a strict prefix of `listForKey`.
- Existing recipe tests (`shared-ui-recipes-field-row`, `…-key-value-list`) still assert `Yes`/`No` text —
  pass unchanged (values unchanged, now centralized).

### Validation
- `npm run check` green — lint + lint:tokens + knip + build + **396 tests** (was 394; +2 query-key tests).
- UAC-10 (nav styling) not browser-verified: `cn()` ≡ `.join(' ')` here (no conflicting Tailwind classes →
  identical class strings); nav active/inactive states covered by existing tests.

### Notes / decisions
- No RD impact. Locked decisions from `phase-1-plan.md` applied (hook co-located; AGENTS.md factual-only this
  pass).

## Phase 1 (part 2) — UAC-01 path aliases  (2026-06-18)

The big mechanical sweep, isolated from part 1.

### Changes
- `tsconfig.app.json` — added `baseUrl: "."` + `paths` for `@/* @app/* @platform/* @modules/* @shared/*
  @styles/*`.
- `vite.config.ts` — matching `resolve.alias` (regex finds, `@/` last) via `fileURLToPath`. Vitest inherits
  this config, so tests resolve aliases too.
- **364 imports across 109 files** rewritten from cross-layer relatives to aliases (one-shot codemod, since
  deleted — not committed). Rule: rewrite when the import crosses a top-level layer (`app/platform/modules/
  shared/styles`) or crosses to a different module under `modules/`; keep intra-module/intra-dir relatives.
  Scope: `src/{app,platform,modules,shared}`; `src/test/**` left as-is (shallow, no 4+ chains).
- `eslint.config.js` — added `no-restricted-imports` backstop banning `../../../../**` (4+ levels) with a
  message pointing to the aliases. (Full layer-zone rules are Phase 3 / UAC-04.)

### Validation
- `grep` confirms **zero** imports with 4+ `../` anywhere in `src`.
- ESLint backstop smoke-tested: a deliberate `../../../../shared/foo` import errors as expected.
- `npm run check` green — lint + lint:tokens + knip + tsc + vite build + **396 tests**. Aliases resolve in
  all three (tsc, vite, vitest).

### Notes / decisions
- Applied locked decisions: layered alias set; thorough cross-layer scope.
- **Phase 1 complete** (6/12). Phase 2 (UAC-02 schema ownership) is decision-gated — needs the RD before code.

## Phase 1 (part 3) — brutal self-eval fixes  (2026-06-18)

A hard, unbiased review of parts 1–2 surfaced gaps; fixed before committing Step 6.

### Changes
- **#1 (scope deviation) — FIXED.** Part 2 had silently excluded `src/test/` from the "thorough/repo-wide"
  alias sweep, leaving 237 cross-layer relative imports in tests. Re-ran the codemod over `src/test` too:
  **238 imports across 82 test files** aliased. Now **zero** cross-layer relatives anywhere in `src`.
- **#2 (docs vs code) — FIXED.** `src/shared/ui/README.md` documented `@/shared/ui` (pre-existing, since
  `e6de1df`) — inconsistent with the layered `@shared/ui` style we adopted. Updated the README examples to
  `@shared/ui` / `@shared/ui/badge`.
- **#3 (undocumented convention) — FIXED.** `ui/AGENTS.md` now documents the path-alias rule (which aliases
  exist, when to use them, the 4+ `../` ESLint ban) in both the "strict UI structure" section and the
  pre-flight checklist.
- **#4 (rule vs reality) — FIXED.** `ui/AGENTS.md` "modules/*/ui" rule reworded to allow page-local
  hooks/helpers beside their page (reusable hooks still belong in `data/`), reconciling the literal rule with
  the co-located `useWorkflowDetailActions` and existing patterns (`useSceneSelection`, `parseWorkflowGraph`).
- **#5b (test drift) — FIXED.** Converted hardcoded key arrays in test assertions
  (`workflow-mutation-hooks`, `workflow-run-detail-page`, `workflow-runs-hooks`, `workflow-hooks`) to the
  `queryKeys` factory. `query-keys.test.ts` remains the single place that pins the literal key shapes.

### Deliberately NOT changed
- **#5a (factory asymmetry).** Only `workflows`/`workflowRuns`/`processedCalls` have `lists()`; `webhooks`/
  `persons` don't (no caller). Adding unused `lists()` for symmetry would be dead code, which the repo's
  knip rule forbids. Helpers stay added-on-demand. Intentional.

### Validation
- `grep`: zero cross-layer relative imports and zero 4+ `../` imports anywhere in `src`; zero hardcoded
  `queryKey: [...]` arrays in tests.
- `npm run check` green — lint + lint:tokens + knip + build + **396 tests**.
