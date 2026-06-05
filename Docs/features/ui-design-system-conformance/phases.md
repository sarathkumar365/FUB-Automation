# Phases — UI Design-System Conformance

Tracker for the active feature. See [`plan.md`](./plan.md) for detail (incl. lifecycle diagrams + RD-impact),
[`research.md`](./research.md) for findings + the review-resolved decisions A–F. Two independent parts; build
in either order. **Every phase that adds code lands its own test (Decision F).**

## Part A — Settings page

| # | Phase | Status |
|---|-------|--------|
| A1 | Foundation — `modules/settings/lib` (schema, sections metadata, **projection — real fields only, no mock**) + projection test | ✅ Complete ([narrative](./phase-a1-implementation.md)) |
| A2 | Platform seam — `settingsPort` (getConfig) + `httpSettingsAdapter` (real GET only) + container + queryKeys | ✅ Complete ([narrative](./phase-a2-implementation.md)) |
| A3 | Data hook — `useSettingsConfigQuery` | ✅ Complete ([narrative](./phase-a3-implementation.md)) |
| A4 | Primitive + icons — `Toggle` (**wraps `@radix-ui/react-switch`**), `SettingsIcon`, `RefreshIcon` (+ barrel) + Toggle test | ✅ Complete ([narrative](./phase-a4-implementation.md)) |
| A5 | UI — `SettingRow`, `ManagedWebhooksCard`, `SettingsPage` (controlled-by-query, "not available" rows, coming-soon gate) + page test | ✅ Complete ([narrative](./phase-a5-implementation.md)) |
| A6 | Wire-in — routes, router, rail `NAV_ICONS`, `uiText` (incl. `comingSoon` + "not available") | ✅ Complete ([narrative](./phase-a6-implementation.md)) |
| A7 | Gate — `npm run check`, browser-verify | ✅ Complete (gate green + live browser verification — see A6 narrative) |

## Part B — Status screens

| # | Phase | Status |
|---|-------|--------|
| B1 | Shared foundation — `FullPageStatus` (own handoff gradient; **lockup via centralized wordmark**), `useRise`, glyphs + test | ✅ Complete ([narrative](./phase-b1-implementation.md)) |
| B2 | Content renderer — `StatusScreen` (Direction B + console strip) + test | ✅ Complete ([narrative](./phase-b2-implementation.md)) |
| B3 | `AppErrorFallback` (bad) + `ErrorDetails` (dev-only) + thread error through both boundaries + test (router-free) | ✅ Complete ([narrative](./phase-b3-implementation.md)) |
| B4 | `NotFoundPage` (brand) — "404" watermark, real-path strip, standalone + in-shell + test | ✅ Complete ([narrative](./phase-b4-implementation.md)) |
| B5 | `SessionDisabledPage` (warn) — lock, helper, no primary, **full-page (route moved out of `AppShell`)** + test | ✅ Complete ([narrative](./phase-b5-implementation.md)) |
| B6 | Copy — extend `uiText` (`appError` / `notFound` / `session`) | ✅ Complete (landed across B3/B4/B5; all status copy centralized in `uiText`) |
| B7 | Gate — `npm run check`, browser-verify (light/dark/in-shell) | ✅ Complete (gate green — 394 tests; family browser-swept light/dark + in-shell, no console errors) |

**Status legend:** ⬜ Not started · 🟡 In progress · ✅ Complete

When all phases are ✅, consolidate to the archived shape (README.md + plan.md + implementation-log.md) per
`Docs/features/README.md` conventions. Each `phase-<n>-implementation.md` must carry its own "Repo decisions
impact" note.
