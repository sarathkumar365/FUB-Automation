# Phase A4 — Toggle primitive + icons (2026-06-04)

## Goal
The shared UI atoms the Settings page (and later status screens) need: an accessible switch and the new
glyphs — sourced from libraries, not hand-rolled.

## What landed
- `shared/ui/Toggle.tsx` — wraps `@radix-ui/react-switch` (Decision E); token-driven track/thumb, focus ring,
  disabled state. Exported from the barrel.
- `shared/ui/icons.tsx` — `SettingsIcon` + `RefreshIcon` from `lucide-react` (Decision G) at stroke 1.8;
  exported from the barrel.
- deps: `@radix-ui/react-switch`, `lucide-react`.
- `src/test/settings-primitives.test.tsx` — Toggle (role/checked/toggles/disabled) + icon smoke render.

## Meaningful decisions
- **Toggle wraps Radix**, so focus/keyboard/`role="switch"`/`aria-checked` come for free; we only own the
  token styling. Consistent with the repo's existing Radix wrappers (`dialog`/`popover`/`tabs`).
- **Icons from lucide at stroke 1.8** to match the existing hand-rolled set; those existing icons are left
  untouched (a wholesale migration would thicken every icon app-wide — out of scope).
- Test imports from the `shared/ui` barrel so the new re-exports are exercised (and stay knip-clean).

## Validation
Test (3 cases) passes. Full `npm run check` green — lint, lint:tokens, knip, build, 371 tests (+3);
`npm audit` 0 vulnerabilities. Not browser-verified: these atoms aren't mounted in the app until A5/A6, where
their on-screen fidelity (tokens, dark mode) is checked.

## Repo decisions impact
**No new repo decision.** Pure presentational primitives; no Accepted decision touched. (Decision G — the
lucide-react adoption — is a feature-local design choice already recorded in this feature's docs, not a
repo-wide RD.)
