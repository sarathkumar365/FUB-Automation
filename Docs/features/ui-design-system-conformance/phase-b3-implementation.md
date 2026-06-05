# Phase B3 — AppErrorFallback + error threading (2026-06-04)

## Goal
Rebuild the error fallback on the status family (bad tone), add a dev-only collapsible stack, and actually
thread the caught error through both boundaries — which they did not do before.

## What landed
- `app/AppErrorFallback.tsx` — rebuilt on `FullPageStatus` + `StatusScreen` (bad tone): Reload (primary),
  plain `<a>` to the dashboard (secondary), dev-only `ErrorDetails` as `extra`. Signature `({ error? })`,
  **no router hooks**.
- `app/status/ErrorDetails.tsx` — collapsed-by-default disclosure; renders `error.message` + `error.stack`;
  gated to `import.meta.env.DEV`.
- `app/RouteErrorBoundary.tsx` — now passes `useRouteError()` to the fallback.
- `app/AppErrorBoundary.tsx` — state changed `hasError → error`; passes the caught error to the fallback.
- `uiText.appError` — added `eyebrow` / `strip` / `details`.
- `src/test/app-error-boundary.test.tsx` — 2 cases (router-free render incl. plain dashboard link; dev-only
  stack reveals the thrown error on expand).

## Meaningful decisions
- **Error is now threaded.** Both boundaries previously discarded the caught error; they now pass it so
  `ErrorDetails` can show it. The fallback takes `error?: unknown` (route errors aren't always `Error`).
- **Dev-only stack.** `ErrorDetails` renders only under `import.meta.env.DEV` — a stack trace must never reach
  production users.
- **Still router-hook-free.** The dashboard action is `StatusScreen`'s `href` secondary → a plain `<a>`, so
  the fallback renders correctly from the class boundary outside RouterProvider (existing regression test kept).

## Validation
Test (2 cases) passes. Full `npm run check` green — 391 tests. Not browser-verified directly (the error
fallback can't be triggered live without crashing the app); its `FullPageStatus` + `StatusScreen` visual is
verified live at B4 via the 404, which renders the identical family.

## Repo decisions impact
**No new repo decision.** Local UI rebuild; the dev-only stack honors the "never expose internals in prod"
posture but introduces no repo-wide decision. RD-004/005/006 not touched.
