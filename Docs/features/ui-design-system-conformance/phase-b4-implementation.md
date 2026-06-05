# Phase B4 — NotFoundPage (2026-06-04)

## Goal
Rebuild the 404 on the status family (brand tone), with the real attempted path in the console strip and both
standalone and in-shell placements. First **live**-verified status screen.

## What landed
- `app/NotFoundPage.tsx` — rebuilt on `FullPageStatus` + `StatusScreen` (brand tone, compass glyph,
  **"404" numeral watermark**); console strip `GET <pathname> · 404` from `useLocation`; primary
  "Go to dashboard" (`navigate(routes.dashboard)`), secondary "Back" (chevron-left, `navigate(-1)`).
  `inShell` prop drops the gradient/lockup.
- `app/router.tsx` — the in-shell `*` route now renders `<NotFoundPage inShell />`; the top-level `*` stays
  standalone.
- `uiText.notFound` — `eyebrow` → "404 · Not found", added `back`.
- `src/test/app-routing.test.tsx` — added an assertion that the strip surfaces the real attempted path.

## Meaningful decisions
- **Router hooks are fine here.** Unlike `AppErrorFallback`, `NotFoundPage` always renders inside
  RouterProvider, so it uses `useLocation`/`useNavigate` (real path in the strip, client-side nav).
- **One renderer, two placements.** Both variants use the same `StatusScreen`; `inShell` only drops the
  shell's gradient + lockup (the four-region shell already provides chrome).

## Validation
Routing test (5 cases) passes. Full `npm run check` green — 391 tests. **Browser-verified live** (dev,
authenticated, dark mode), no console errors:
- **Standalone** (`/totally-unknown-page`): `FullPageStatus` gradient + `BrandLockup` wordmark, the "404"
  ghost watermark, strip `GET /totally-unknown-page · 404`, Dashboard + Back actions.
- **In-shell** (`/admin-ui/does-not-exist`): renders inside the shell (rail still visible), no gradient/lockup,
  strip `GET /admin-ui/does-not-exist · 404`, "404" watermark.

This also transitively confirms B1 (`FullPageStatus`) and B2 (`StatusScreen`) render correctly live.

## Repo decisions impact
**No new repo decision.** Local UI rebuild; RD-004/005/006 not touched (RD-005 wordmark reused via BrandLockup).
