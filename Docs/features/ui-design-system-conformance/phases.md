# Phases — UI Design-System Conformance

Tracker for the active feature. See [`plan.md`](./plan.md) for detail, [`research.md`](./research.md) for
findings. Two independent parts; build in either order.

## Part A — Settings page

| # | Phase | Status |
|---|-------|--------|
| A1 | Foundation — `modules/settings/lib` (schema, sections metadata, projection) + unit test | ⬜ Not started |
| A2 | Platform seam — `settingsPort` + `httpSettingsAdapter` (real GET + mock-fenced) + container + queryKeys | ⬜ Not started |
| A3 | Data hook — `useSettingsConfigQuery` | ⬜ Not started |
| A4 | Primitive + icons — `Toggle`, `SettingsIcon`, `RefreshIcon` (+ barrel) | ⬜ Not started |
| A5 | UI — `SettingRow`, `ManagedWebhooksCard`, `SettingsPage` (section-nav, coming-soon gate) | ⬜ Not started |
| A6 | Wire-in — routes, router, rail `NAV_ICONS`, `uiText` (incl. `comingSoon`) | ⬜ Not started |
| A7 | Tests + gates — projection + page specs, `npm run check`, browser-verify | ⬜ Not started |

## Part B — Status screens

| # | Phase | Status |
|---|-------|--------|
| B1 | Shared foundation — `FullPageStatus`, shared gradient (refactor `AuthShell`), `useRise`, glyphs | ⬜ Not started |
| B2 | Content renderer — `StatusScreen` (Direction B + console strip) | ⬜ Not started |
| B3 | `AppErrorFallback` (bad) + `ErrorDetails` (dev-only) + thread error through both boundaries | ⬜ Not started |
| B4 | `NotFoundPage` (brand) — "404" watermark, real-path strip, standalone + in-shell | ⬜ Not started |
| B5 | `SessionDisabledPage` (warn) — lock, helper, no primary | ⬜ Not started |
| B6 | Copy — extend `uiText` (`appError` / `notFound` / `session`) | ⬜ Not started |
| B7 | Tests + gates — `status-screens.test.tsx`, `npm run check`, browser-verify (light/dark/in-shell) | ⬜ Not started |

**Status legend:** ⬜ Not started · 🟡 In progress · ✅ Complete

When all phases are ✅, consolidate to the archived shape (README.md + plan.md + implementation-log.md) per
`Docs/features/README.md` conventions.
