# Phase A5 — Settings UI (2026-06-04)

## Goal
Render the Settings page: the "Configuration" panel section-nav + the section cards, consuming the A3 hook.
First visible phase (route wiring is A6, so it's not yet reachable in the running app).

## What landed
- `modules/settings/ui/SettingRow.tsx` — one row's label/description/key + its control.
- `modules/settings/ui/ManagedWebhooksCard.tsx` — section note + "Sync now" + the not-available empty state.
- `modules/settings/ui/SettingsPage.tsx` — panel section-nav (via `useShellRegionRegistration`), header,
  active section card, loading/error states.
- `src/test/settings-page.test.tsx` — 6 cases.

## Meaningful decisions
- **Controls controlled by the query data; no local state.** Each control reflects the live value via the
  row's `read` selector and never mutates. Interaction surfaces `notify.info(comingSoon)`:
  - toggle / select → `onChange`/`onCheckedChange` (one event per change attempt; value reverts);
  - text / number → `readOnly` + `onMouseDown` (shows the value, avoids a per-keystroke toast storm);
  - secret → rendered as **"Configured" / "Not configured"** status (no input — the value is redacted);
  - unbacked rows → **"Not available yet"** (no fabricated value), per Decision B.
- **Managed-webhooks shows an empty "not available" state, not mock rows** — "Sync now" defers to coming-soon.
- The page branches on the hook's `isPending` / `isError` with the shared `LoadingState` / `ErrorState`.

## Validation
Page test (6 cases) passes — default render from real config, coming-soon on interaction, the one backed flag
+ "not available" for the three unexposed, managed empty state, loading, error. Full `npm run check` green —
378 tests (+7). **Not browser-verified yet:** the page is unrouted until A6; on-screen fidelity (tokens, dark
mode, design match) is checked there.

## Repo decisions impact
**No new repo decision.** Honors **RD-006**: the `engine.write.emit-events` flag renders "not available yet"
— never fabricated or shown as a live toggle, so the echo-safety posture is never misrepresented. RD-004 (auth)
is transitive via the read.
