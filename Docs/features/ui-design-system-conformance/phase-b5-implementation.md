# Phase B5 — SessionDisabledPage (2026-06-05)

## Goal
Rebuild the session-disabled screen on the status family (warn tone) and, per **Decision D**, promote it to a
**full-page** route — out of `AppShell`, on the gradient — since the session guard turns off all console
access and there is nothing actionable inside the shell chrome.

## What landed
- `app/SessionDisabledPage.tsx` — rebuilt on `FullPageStatus` + `StatusScreen` (warn tone, lock glyph,
  "lock" ghost watermark). Eyebrow `Restricted`, title "Admin access is disabled", body, an emphasized
  helper line ("ask a **workspace administrator**…"), console strip `GUARD · admin-ui access disabled`.
  **No primary action** — only a quiet secondary `Return to sign in` link (`<a href={routes.login}>`).
  Dropped `useShellRegionRegistration` / `PageHeader` / `PageCard` / `EmptyState` (no longer in the shell).
- `app/router.tsx` — the `session-disabled` route moved from an `AppShell` child to a **top-level** route
  (`routes.sessionDisabled`, with its own `errorElement`). React Router ranks the static path above the
  shell's `/admin-ui/*`, so the `SessionGuard` redirect lands on the full-page screen.
- `shared/constants/uiText.ts` — `session` block reshaped: `eyebrow`/`title`/`body`/`helperLead`/
  `helperEmphasis`/`helperTail`/`strip`/`returnToSignIn` (replacing `disabledMessage`/`disabledPanelNote`).
- `src/test/session-disabled.test.tsx` — new (2 cases): warn screen renders title/body/strip + emphasized
  helper; only a `Return to sign in` link and **no** primary button.
- `src/test/shell-regions.test.tsx` — the session-disabled case rewritten: a guarded route now redirects to
  the **full-page** screen (asserts title/body/link + **no** shell rail), not an in-shell render.

## Meaningful decisions
- **Full-page, route out of `AppShell` (Decision D).** A disabled session has no usable console, so showing
  rail/panel/inspector would imply controls that do not work. The page stands alone on the gradient.
- **No primary action (handoff).** There may be nothing the user can do; the helper line carries the real
  guidance ("ask a workspace administrator"). Only a legitimate `Return to sign in` link.
- **Plain `<a href>` for the return link.** Leaving a disabled session is a fresh load, not in-app nav.

## Validation
`session-disabled` (2) + `shell-regions` (4) pass. Full `npm run check` green — **394 tests**.
**Browser-verified live** (dev, dark mode), no console errors:
- Direct `/admin-ui/session-disabled`: lock watermark + `BrandLockup` wordmark on the gradient, eyebrow
  "Restricted", title "Admin access is disabled", body, emphasized "workspace administrator" helper, strip
  `GUARD · admin-ui access disabled`, `Return to sign in` → `/admin-ui/login`, **no** primary button.
- Guard redirect: `admin-ui-enabled=false` + visiting `/admin-ui/webhooks` redirects to
  `/admin-ui/session-disabled`, full-page (**no shell rail**).

## Repo decisions impact
**No new repo decision.** Implements the already-recorded **Decision D** (latest handoff: full-page, route out
of `AppShell`). RD-004/005/006 untouched (RD-005 wordmark reused via `BrandLockup`).
