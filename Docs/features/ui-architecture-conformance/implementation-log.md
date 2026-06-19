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
