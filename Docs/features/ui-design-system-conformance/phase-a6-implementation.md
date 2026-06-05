# Phase A6 — Wire-in + browser verification (2026-06-04)

## Goal
Make the Settings page reachable, then verify it live against the real backend. (Covers A6 wiring and the
A7 gate + browser verification.)

## What landed
- `shared/constants/routes.ts` — `AppNavKey += 'settings'`, `routes.settings`, `appNavItems` entry.
- `app/router.tsx` — `settings` route under `SessionGuard`→`AuthGuard`.
- `shared/ui/AppRail.tsx` — `NAV_ICONS.settings` → the lucide `SettingsIcon`.
- `shared/constants/uiText.ts` — `nav.settings`.

## Validation — gate + live browser
- Full `npm run check` green: lint, lint:tokens, knip, build, **378 tests**.
- **Browser-verified** at `/admin-ui/settings` (dev: vite :5173 + backend :8080, authenticated), dark mode:
  - Page reachable; rail shows the **Settings** gear (lucide) and it's the active item. No console errors.
  - **Business hours** renders real config: timezone `America/Toronto`, start `9`, end `18`, weekdays-only `On`.
  - **Feature flags** shows exactly **one backed toggle** (`Follow Up Boss source`) and **three "Not available
    yet"** rows for the unexposed flags — Decision B confirmed on screen.
  - **Connections** shows the Connected badge, real `Base URL` / `X-System` (read-only), and both secrets as
    **"Configured"** — presence only, no key value in the DOM.
  - **Managed webhooks** shows the "not available yet" empty state + a "Sync now" action.
  - Touching a control fires the **"This editing feature is coming soon."** toast and the control does **not**
    mutate (`aria-checked` unchanged) — the controlled-by-query / no-local-state model holds live.

## Repo decisions impact
**No new repo decision.** RD-006 verified on screen (engine-echo flag renders "not available", never a live
toggle). RD-004 (auth) exercised end-to-end — the page loaded only because the JWT session was valid; the
guarded route + bearer read worked. RD-005 wordmark is a Part B (status-screens) concern, untouched here.
