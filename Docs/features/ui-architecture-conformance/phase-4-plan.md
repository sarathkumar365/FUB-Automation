# Phase 4 — Code-split the workflow graph / dagre (UAC-03)

Goal: get `@dagrejs/dagre` (and the storyboard render surface) out of the **initial** JS bundle so pages that
never show a workflow graph (dashboard, webhooks, calls, persons, settings, runs, workflow *list*) don't
download it. Performance-only; no behavior change. Per review-before-commit: implement → present → wait.

> **This is optional.** Do it only if initial load for graph-free users is worth a small amount of lazy-load
> machinery + a possible loading flash on the workflow-detail page. The "measure first" step below de-risks the
> decision with real numbers before any test churn.

---

## What actually pulls in dagre (the load-bearing finding)

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

## Step 0 — Measure first (decide on real numbers)
`npm run build` today: single `index-*.js` = **730 kB / 217 kB gzip** (no splitting). Add a throwaway
`build.rollupOptions`/`manualChunks` or just `lazy()` the two boundaries on a scratch branch and re-build to see
the dagre/storyboard chunk size. Estimate ~**30–45 kB gzip** for dagre + a bit for the surface — i.e. a ~15–20%
initial-JS cut for graph-free users. **If the measured win is too small to justify the flash (below), stop here
and mark UAC-03 won't-fix.**

## The central decision (pick before implementing)
| Option | What | Cost |
|---|---|---|
| **A — full split (recommended)** | lazy the builder route **and** the detail `StoryboardTab` | dagre leaves main; brief loading flash on the detail page's default tab (first visit, then cached) |
| **B — builder-only** | lazy only the builder route | no flash on common pages, but **dagre stays in main** (detail tab uses it) → small win (splits builder chrome/`builderStore`/`debugOverlay`, not dagre) |
| **C — A + prefetch** | A, plus prefetch the storyboard chunk on workflows-list/detail mount or row hover | near-zero flash; a little more code |

Recommendation: **A**, optionally **C** if the detail-page flash is objectionable. B doesn't achieve the
finding's goal (dagre is the heavy bit and it's in the detail tab).

---

## Implementation (Option A)
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

## Test impact — small (most tests untouched)
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

## Risks
- **Loading flash on the default detail tab** (Option A) — the most-common workflow view gains a brief fallback
  on first visit until the chunk loads (then cached). Mitigate with Option C prefetch if it matters.
- **Suspense in the router** — `createBrowserRouter` route `element`s wrapped in `<Suspense>` is supported;
  alternatively use React Router v7's route-level `lazy` API (cleaner, but restructures route defs). Plan uses
  `React.lazy + Suspense` (smaller diff).
- **Named-export mapping** — `lazy()` needs a default; use the `.then((m) => ({ default: m.X }))` form.
- **jsdom dynamic import** — resolves fast in tests; `findBy*` handles the async. Watch for any test using
  synchronous `getBy*` on storyboard content (none found).

## Acceptance
- `npm run build`: dagre + storyboard surface in a **separate async chunk**, absent from the initial `index-*.js`;
  main chunk shrinks by the measured amount. Graph-free routes load without the storyboard chunk (verify via
  the build's chunk list / network in preview).
- `npm run check` green (396+ tests); the full-page detail test passes with the lazy tab.
- UAC-03 ticked; README/tracker/log updated.

## Non-goals
- General vendor chunking (React/router/query/zod/radix/lucide splitting) — separate optimization, not UAC-03.
- Lazy-loading other routes (no heavy deps; not worth the flash).
- Changing the storyboard/builder behavior or the dagre layout itself.
