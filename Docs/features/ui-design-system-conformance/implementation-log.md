# Implementation Log — UI Design-System Conformance

Append-only, one dated section per phase. Design + research live in [`plan.md`](./plan.md); entry point +
final tracker in [`README.md`](./README.md). Two parts: **A = Settings page**, **B = Status screens**.
Every phase landed its own test (Decision F). No phase introduced a new repo-wide decision; each consumes
existing `Accepted` RD-004 (JWT auth) / RD-005 (wordmark single-source) / RD-006 (engine-echo safe-by-default).

---

## A1 — Settings foundation lib (2026-06-04)
Pure, testable foundation (no UI, no network): `modules/settings/lib/{settingsSchemas,settingsProjection,settingsSections}.ts`
+ `settings-projection.test.ts` (6 cases).
- **Show only real data (Decision B):** display model carries only backend-returned fields; the 3 unexposed
  flags + managed-webhooks are `backed: false` → "not available yet", never fabricated. Test pins that only
  `webhook.sources.fub.enabled` is backed.
- Secrets collapse to presence-only (`{ present }`); a test asserts the `***` sentinel never enters the model.
- Schema strips unsurfaced fields (`fubRetry`/`callRules`/extra `webhook.*`) — no dependency on unrendered shapes.
- Value selectors live on row metadata (`read`/`readSecretPresent`); `backed ⇔ hasReader` is test-enforced.
- `uiText.settings` namespace added here (needed by `settingsSections`). **RD-006 encoded** at the lib layer.
- Gate green — 365 tests (+6).

## A2 — Settings platform seam (2026-06-04)
Real read path: `platform/ports/settingsPort.ts` (`getConfig()` only — `updateConfig` left as a future-write
comment, not a stub), `platform/adapters/http/httpSettingsAdapter.ts` (thin: `httpClient.get(path, schema)`
→ `projectSettingsConfig`), registered in `container.ts`, `queryKeys.settings.config()` + `settings-adapter.test.ts`.
- Zod validation stays inside `HttpJsonClient.get`; the adapter only supplies path+schema and projects.
- Consumes **RD-004** (bearer attached for `/admin/**`). Gate green — 366 tests (+1).

## A3 — Settings data hook (2026-06-04)
`modules/settings/data/useSettingsConfigQuery.ts` (`useQuery` over the port, keyed by `queryKeys.settings.config()`)
+ `settings-hooks.test.tsx` (success + error). Thin wrapper, mirrors `useDashboardSnapshotQuery`; server state
stays in TanStack Query (no local copy). Gate green — 368 tests (+2).

## A4 — Toggle primitive + icons (2026-06-04)
`shared/ui/Toggle.tsx` wrapping `@radix-ui/react-switch` (Decision E — focus/keyboard/`role="switch"` for free,
token-only styling); `SettingsIcon` + `RefreshIcon` from `lucide-react` (Decision G) at stroke 1.8 via the
barrel; deps added; `settings-primitives.test.tsx` (3 cases). Existing hand-rolled icons left untouched
(lucide defaults to stroke 2 — a wholesale migration would thicken every icon, out of scope). `npm audit` 0
vulns. Gate green — 371 tests (+3).

## A5 — Settings UI (2026-06-04)
`modules/settings/ui/{SettingRow,ManagedWebhooksCard,SettingsPage}.tsx` + `settings-page.test.tsx` (6 cases).
- **Controlled-by-query, zero local state (Decision C):** controls reflect the live value and never mutate;
  interaction fires `notify.info(comingSoon)`. toggle/select → change handler (value reverts); text/number →
  `readOnly` + `onMouseDown` (no per-keystroke toast storm); secret → "Configured"/"Not configured" (redacted,
  no input); unbacked rows → "Not available yet" (Decision B).
- Managed-webhooks = "not available" empty state, no mock rows; "Sync now" defers to coming-soon.
- Page branches on `isPending`/`isError` via shared `LoadingState`/`ErrorState`. **RD-006** honored on screen-model.
- Gate green — 378 tests (+7). Not browser-verified yet (unrouted until A6).

## A6/A7 — Wire-in + live browser verification (2026-06-04)
`routes.ts` (`AppNavKey += 'settings'`, `routes.settings`, `appNavItems`), `router.tsx` (route under
`SessionGuard`→`AuthGuard`), `AppRail.tsx` (`NAV_ICONS.settings`), `uiText.nav.settings`.
- **Browser-verified** at `/admin-ui/settings` (vite :5173 + backend :8080, authenticated, dark mode), no
  console errors: rail gear active; Business hours real config (timezone `America/Toronto`, 9/18, weekdays-only
  On); Feature flags = exactly one backed toggle + three "Not available yet" (Decision B on screen); Connections
  shows Connected badge, real Base URL / X-System, both secrets "Configured" (no key in DOM); Managed webhooks
  empty state + "Sync now"; touching a control fires the coming-soon toast and does not mutate (`aria-checked`
  unchanged). RD-006 verified on screen; RD-004 exercised end-to-end. Gate green — 378 tests.

## B1 — Status shared foundation (2026-06-04)
`app/status/FullPageStatus.tsx` (own handoff gradient + top-centre lockup + centred slot; props `inShell` /
`hideLockup`; root `isolation: isolate`; router-hook-free), `shared/lib/useRise.ts` (transform-only WAAPI rise,
`prefers-reduced-motion`-guarded), status glyphs (alert-triangle, compass, lock, chevron-left, chevron-down)
from `lucide-react` via the barrel; `status-foundation.test.tsx` (5 cases).
- Own gradient constant — **AuthShell untouched** (near-but-different values; unify is out of scope).
- Lockup reuses `uiText.authShell.wordmark` (**RD-005** single-source). Transform-only entrance can't strand
  content hidden. Router-hook-free so B3 can render it from the class boundary. Gate green — 385 tests (+5).

## B2 — StatusScreen renderer (2026-06-04)
`app/status/StatusScreen.tsx` + the `StatusContent` shape (tone, glyph, copy, meta strip, optional
helper/watermark/primary/secondary/extra) + `status-screen.test.tsx` (5 cases).
- Tone = colour **+ a word** (`TONES` → `{fg,bg}` token vars; eyebrow never a bare colour).
- Secondary action polymorphic: `href` → plain `<a>` (router-hook-free); `onClick` → `<button>`. Primary = brand `Button`.
- Watermark overridable (the "404" numerals); glyph sizing via `className` relies on `cn`'s `twMerge`. Gate green — 390 tests (+5).

## B3 — AppErrorFallback + error threading (2026-06-04)
`app/AppErrorFallback.tsx` rebuilt on the family (bad tone): Reload primary, plain `<a>` dashboard secondary,
dev-only `ErrorDetails` extra; `({ error? })`, no router hooks. `app/status/ErrorDetails.tsx` (collapsed-by-default,
`error.message`+`error.stack`, gated to `import.meta.env.DEV`). `RouteErrorBoundary` + `AppErrorBoundary` now
**thread the caught error** (previously discarded). `uiText.appError` += eyebrow/strip/details.
`app-error-boundary.test.tsx`.
- Dev-only stack never reaches production; still router-hook-free (regression test kept). Gate green — 391 tests.
- *(Later, in B5 review:)* `AppErrorBoundary` state reverted to a `hasError` flag so `throw null` shows the
  fallback (not a blank screen); throw-null regression test added.

## B4 — NotFoundPage (2026-06-04)
`app/NotFoundPage.tsx` rebuilt (brand tone, compass, **"404" numeral watermark**), strip `GET <pathname> · 404`
from `useLocation`, primary "Go to dashboard" + secondary "Back"; `inShell` drops gradient/lockup. `router.tsx`
in-shell `*` → `<NotFoundPage inShell />`. `app-routing.test.tsx` += real-path strip assertion.
- Router hooks fine here (always inside RouterProvider). One renderer, two placements.
- **First live status screen** — browser-verified standalone (`/totally-unknown-page`) + in-shell
  (`/admin-ui/does-not-exist`), transitively confirming B1/B2. Gate green — 391 tests.
- *(Later, in B5 review:)* "Back" made conditional on `window.history.length > 1` (no dead button on a direct landing).

## B5/B6/B7 — SessionDisabledPage + close-out (2026-06-05)
`app/SessionDisabledPage.tsx` rebuilt on the family (warn tone, lock glyph/watermark, emphasized
"workspace administrator" helper, strip `GUARD · admin-ui access disabled`, **no primary**, quiet
"Return to sign in" link). Per **Decision D**, route promoted **out of `AppShell`** to a top-level full-page
route (out-ranks `/admin-ui/*`, outside `SessionGuard` → no redirect loop). `uiText.session` reshaped.
`session-disabled.test.tsx` (2 cases) + `shell-regions.test.tsx` rewritten to expect the full-page redirect
(no shell chrome).
- **B6 (copy):** all status copy centralized in `uiText` (`appError`/`notFound`/`session`) — landed across B3/B4/B5.
- **B7 (gate + browser):** gate green — **394 tests**; status family browser-swept light + dark + in-shell, no
  console errors. 404 confirmed token-remap in both themes (light bg `#f7f9fc`/text `#0f172a`, dark
  `#0b1220`/`#e8eef9`); session-disabled full-page (no rail) + guard redirect confirmed. `AppErrorFallback`
  stays unit-verified (live trigger needs a real crash; "no temp boom" per user).
