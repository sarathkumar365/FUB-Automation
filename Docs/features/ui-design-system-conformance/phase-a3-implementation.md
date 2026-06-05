# Phase A3 — Settings data hook (2026-06-04)

## Goal
Connect the `SettingsPort` (A2) to React via TanStack Query so the page (A5) can consume the config with
caching, loading, and error states.

## What landed
- `modules/settings/data/useSettingsConfigQuery.ts` — `useQuery` over `settingsPort.getConfig()` keyed by
  `queryKeys.settings.config()`.
- `src/test/settings-hooks.test.tsx` — success (returns projected config) + error (port throws → `isError`).

## Meaningful decisions
- **Thin wrapper, no local state.** Mirrors `useDashboardSnapshotQuery`; server state stays in TanStack Query
  (per the UI rule), so the page branches on `isPending` / `isError` / `data` without copying anything.

## Validation
Hook test (2 cases) passes. Full `npm run check` green — lint, lint:tokens, knip, build, 368 tests (+2).

## Repo decisions impact
**No new repo decision.** The read rides RD-004 (JWT bearer) transitively through the port/`HttpJsonClient`.
RD-005/006 not touched at this layer.
