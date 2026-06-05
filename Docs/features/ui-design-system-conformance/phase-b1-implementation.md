# Phase B1 — Status screens shared foundation (2026-06-04)

## Goal
The shared pieces all three status screens build on: the `FullPageStatus` shell, the entrance-motion hook,
and the status glyphs. No page is rebuilt yet (those are B3–B5).

## What landed
- `app/status/FullPageStatus.tsx` — ambient brand gradient + top-centre brand lockup + centred slot; props
  `inShell` (drops gradient + lockup) and `hideLockup`; root `isolation: isolate`. Router-hook-free.
- `shared/lib/useRise.ts` — transform-only WAAPI entrance rise, `prefers-reduced-motion`-guarded.
- `shared/ui/icons.tsx` — `AlertTriangleIcon`, `CompassIcon`, `LockIcon`, `ChevronLeftIcon`, `ChevronDownIcon`
  from `lucide-react` (normalised: stroke 1.8, aria-hidden, 16px default) + barrel exports.
- `src/test/status-foundation.test.tsx` — 5 cases.

## Meaningful decisions
- **Own gradient, AuthShell untouched.** `FullPageStatus` uses the handoff gradient values in its own
  constant; `AuthShell`'s near-but-different gradient is left alone (a wholesale unify would shift the auth
  screens — out of scope, per the design review).
- **Lockup reuses the centralized wordmark** (`uiText.authShell.wordmark` / `wordmarkSub`) — no new
  "Automation Engine" literals, so RD-005's pending rename stays single-source.
- **Transform-only entrance.** `useRise` never touches opacity, so a throttled/background tab can't strand
  content hidden; reduced-motion users skip it entirely.
- **Router-hook-free shell** so it can render from the top-level class error boundary in B3.

## Validation
Test (5 cases) passes. Full `npm run check` green — 385 tests (+5). Not browser-verified: nothing renders
these yet; on-screen fidelity is checked when the pages land (B3–B5).

## Repo decisions impact
**No new repo decision.** Honors **RD-005** (wordmark single-source via `uiText`). RD-004/006 not touched.
