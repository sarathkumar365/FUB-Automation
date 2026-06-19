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
