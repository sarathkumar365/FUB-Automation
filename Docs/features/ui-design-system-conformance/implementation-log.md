# Implementation Log — UI Design-System Conformance

Append-only history — one dated section per phase, absorbed **verbatim** from the per-phase
narratives. Design + research live in [`plan.md`](./plan.md); entry point + tracker in
[`README.md`](./README.md).

---

## Phase A1 — Settings foundation lib (2026-06-04)

### Goal
Pure, testable foundation for the Settings page — no UI, no network. The Zod contract for
`GET /admin/settings/config`, the page-facing display model, the response→display projection, and the
section/row metadata that later phases render against.

### What landed
- `modules/settings/lib/settingsSchemas.ts` — wire schema (only the fields the page surfaces) + display
  `SettingsConfig` / `SecretState` types.
- `modules/settings/lib/settingsProjection.ts` — `projectSettingsConfig(response)`.
- `modules/settings/lib/settingsSections.ts` — `SETTINGS_SECTIONS` (4 sections, dotted keys, control kinds,
  `backed` flags, value selectors); copy pulled from a new `uiText.settings` namespace.
- `src/test/settings-projection.test.ts` — 6 cases (projection + metadata invariants).

### Meaningful decisions
- **Show only real data (Decision B).** The display model carries *only* backend-returned fields. Settings the
  design shows but the endpoint doesn't expose (3 of 4 feature flags; managed-webhooks) are not fabricated —
  they're marked `backed: false` in the row metadata so the UI can render "not available yet". A test pins
  this: only `webhook.sources.fub.enabled` is backed; the other three flags carry no reader.
- **Secrets are presence-only.** Projection collapses `Redactable{present,value}` to `{ present }`; a test
  asserts the redaction sentinel `***` never enters the display model.
- **Schema is lenient on unsurfaced fields.** `fubRetry`/`callRules`/extra `webhook.*` are returned by the API
  but not modelled — Zod strips them (test feeds them in and expects no error), so we don't take a dependency
  on shapes we don't render.
- **Selectors live on the row metadata.** Each backed row carries `read` (or `readSecretPresent`) so the UI
  binds values without a key→value switch; a `backed ⇔ hasReader` invariant is test-enforced.
- **`uiText.settings` added now, not in A6.** The section/row copy is needed by `settingsSections`, so the copy
  namespace landed here; A6's wire-in is just `nav.settings` + the route.

### Trade-offs / surprises
- Section metadata sits in `lib/` (not `ui/`) so it's unit-testable before any component exists — a small
  altitude choice that pays off in A5. No surprises; `knip` stays clean because the test (a knip entry) imports
  every cross-file export.

### Validation
- New test: 6/6 pass. Full `npm run check` green — lint, lint:tokens (no hex), knip (no dead code), build,
  365 tests (+6).

### Repo decisions impact
**No new repo decision.** A1 is pure frontend lib. It *encodes* RD-006 (engine-echo safe-by-default): the
RD-006-governed `engine.write.emit-events` flag is modelled `backed: false` so the UI renders "not available
yet" and never fabricates a value that could misrepresent the echo-safety posture. RD-004 (auth) and RD-005
(wordmark) are not touched at this layer.

---

## Phase A2 — Settings platform seam (2026-06-04)

### Goal
Wire the real read path: a `SettingsPort` and its HTTP adapter that calls `GET /admin/settings/config` through
the existing central client, registered in the DI container, with a query key for A3.

### What landed
- `platform/ports/settingsPort.ts` — `SettingsPort { getConfig(): Promise<SettingsConfig> }`.
- `platform/adapters/http/httpSettingsAdapter.ts` — `HttpSettingsAdapter`: `httpClient.get(path, schema)`
  then `projectSettingsConfig`.
- `platform/container.ts` — `settingsPort` registered in `appPorts` / `AppPorts`.
- `platform/query/queryKeys.ts` — `queryKeys.settings.config()`.
- `src/test/settings-adapter.test.ts` — adapter reads the right path and returns the projected config.

### Meaningful decisions
- **Port is read-only for now.** `updateConfig` is left as a one-line comment (future write API), not an
  interface method — no unused stub to satisfy.
- **Adapter stays thin.** Zod validation happens inside `HttpJsonClient.get`; the adapter only supplies the
  path + schema and projects. The test uses a fake client that parses the sample with the passed schema, so
  it exercises the real validate→project wiring without a network.

### Validation
Adapter test passes. Full `npm run check` green — lint, lint:tokens, knip, build, 366 tests (+1).

### Repo decisions impact
**No new repo decision.** Consumes **RD-004 (JWT bearer auth)**: the read rides `HttpJsonClient`, which
attaches the `Authorization: Bearer` header for `/admin/**` paths — no parallel auth. RD-005/006 not touched
at this layer.

---

## Phase A3 — Settings data hook (2026-06-04)

### Goal
Connect the `SettingsPort` (A2) to React via TanStack Query so the page (A5) can consume the config with
caching, loading, and error states.

### What landed
- `modules/settings/data/useSettingsConfigQuery.ts` — `useQuery` over `settingsPort.getConfig()` keyed by
  `queryKeys.settings.config()`.
- `src/test/settings-hooks.test.tsx` — success (returns projected config) + error (port throws → `isError`).

### Meaningful decisions
- **Thin wrapper, no local state.** Mirrors `useDashboardSnapshotQuery`; server state stays in TanStack Query
  (per the UI rule), so the page branches on `isPending` / `isError` / `data` without copying anything.

### Validation
Hook test (2 cases) passes. Full `npm run check` green — lint, lint:tokens, knip, build, 368 tests (+2).

### Repo decisions impact
**No new repo decision.** The read rides RD-004 (JWT bearer) transitively through the port/`HttpJsonClient`.
RD-005/006 not touched at this layer.

---

## Phase A4 — Toggle primitive + icons (2026-06-04)

### Goal
The shared UI atoms the Settings page (and later status screens) need: an accessible switch and the new
glyphs — sourced from libraries, not hand-rolled.

### What landed
- `shared/ui/Toggle.tsx` — wraps `@radix-ui/react-switch` (Decision E); token-driven track/thumb, focus ring,
  disabled state. Exported from the barrel.
- `shared/ui/icons.tsx` — `SettingsIcon` + `RefreshIcon` from `lucide-react` (Decision G) at stroke 1.8;
  exported from the barrel.
- deps: `@radix-ui/react-switch`, `lucide-react`.
- `src/test/settings-primitives.test.tsx` — Toggle (role/checked/toggles/disabled) + icon smoke render.

### Meaningful decisions
- **Toggle wraps Radix**, so focus/keyboard/`role="switch"`/`aria-checked` come for free; we only own the
  token styling. Consistent with the repo's existing Radix wrappers (`dialog`/`popover`/`tabs`).
- **Icons from lucide at stroke 1.8** to match the existing hand-rolled set; those existing icons are left
  untouched (a wholesale migration would thicken every icon app-wide — out of scope).
- Test imports from the `shared/ui` barrel so the new re-exports are exercised (and stay knip-clean).

### Validation
Test (3 cases) passes. Full `npm run check` green — lint, lint:tokens, knip, build, 371 tests (+3);
`npm audit` 0 vulnerabilities. Not browser-verified: these atoms aren't mounted in the app until A5/A6, where
their on-screen fidelity (tokens, dark mode) is checked.

### Repo decisions impact
**No new repo decision.** Pure presentational primitives; no Accepted decision touched. (Decision G — the
lucide-react adoption — is a feature-local design choice already recorded in this feature's docs, not a
repo-wide RD.)

---

## Phase A5 — Settings UI (2026-06-04)

### Goal
Render the Settings page: the "Configuration" panel section-nav + the section cards, consuming the A3 hook.
First visible phase (route wiring is A6, so it's not yet reachable in the running app).

### What landed
- `modules/settings/ui/SettingRow.tsx` — one row's label/description/key + its control.
- `modules/settings/ui/ManagedWebhooksCard.tsx` — section note + "Sync now" + the not-available empty state.
- `modules/settings/ui/SettingsPage.tsx` — panel section-nav (via `useShellRegionRegistration`), header,
  active section card, loading/error states.
- `src/test/settings-page.test.tsx` — 6 cases.

### Meaningful decisions
- **Controls controlled by the query data; no local state.** Each control reflects the live value via the
  row's `read` selector and never mutates. Interaction surfaces `notify.info(comingSoon)`:
  - toggle / select → `onChange`/`onCheckedChange` (one event per change attempt; value reverts);
  - text / number → `readOnly` + `onMouseDown` (shows the value, avoids a per-keystroke toast storm);
  - secret → rendered as **"Configured" / "Not configured"** status (no input — the value is redacted);
  - unbacked rows → **"Not available yet"** (no fabricated value), per Decision B.
- **Managed-webhooks shows an empty "not available" state, not mock rows** — "Sync now" defers to coming-soon.
- The page branches on the hook's `isPending` / `isError` with the shared `LoadingState` / `ErrorState`.

### Validation
Page test (6 cases) passes — default render from real config, coming-soon on interaction, the one backed flag
+ "not available" for the three unexposed, managed empty state, loading, error. Full `npm run check` green —
378 tests (+7). **Not browser-verified yet:** the page is unrouted until A6; on-screen fidelity (tokens, dark
mode, design match) is checked there.

### Repo decisions impact
**No new repo decision.** Honors **RD-006**: the `engine.write.emit-events` flag renders "not available yet"
— never fabricated or shown as a live toggle, so the echo-safety posture is never misrepresented. RD-004 (auth)
is transitive via the read.

---

## Phase A6 — Wire-in + browser verification (2026-06-04)

### Goal
Make the Settings page reachable, then verify it live against the real backend. (Covers A6 wiring and the
A7 gate + browser verification.)

### What landed
- `shared/constants/routes.ts` — `AppNavKey += 'settings'`, `routes.settings`, `appNavItems` entry.
- `app/router.tsx` — `settings` route under `SessionGuard`→`AuthGuard`.
- `shared/ui/AppRail.tsx` — `NAV_ICONS.settings` → the lucide `SettingsIcon`.
- `shared/constants/uiText.ts` — `nav.settings`.

### Validation — gate + live browser
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

### Repo decisions impact
**No new repo decision.** RD-006 verified on screen (engine-echo flag renders "not available", never a live
toggle). RD-004 (auth) exercised end-to-end — the page loaded only because the JWT session was valid; the
guarded route + bearer read worked. RD-005 wordmark is a Part B (status-screens) concern, untouched here.

---

## Phase B1 — Status screens shared foundation (2026-06-04)

### Goal
The shared pieces all three status screens build on: the `FullPageStatus` shell, the entrance-motion hook,
and the status glyphs. No page is rebuilt yet (those are B3–B5).

### What landed
- `app/status/FullPageStatus.tsx` — ambient brand gradient + top-centre brand lockup + centred slot; props
  `inShell` (drops gradient + lockup) and `hideLockup`; root `isolation: isolate`. Router-hook-free.
- `shared/lib/useRise.ts` — transform-only WAAPI entrance rise, `prefers-reduced-motion`-guarded.
- `shared/ui/icons.tsx` — `AlertTriangleIcon`, `CompassIcon`, `LockIcon`, `ChevronLeftIcon`, `ChevronDownIcon`
  from `lucide-react` (normalised: stroke 1.8, aria-hidden, 16px default) + barrel exports.
- `src/test/status-foundation.test.tsx` — 5 cases.

### Meaningful decisions
- **Own gradient, AuthShell untouched.** `FullPageStatus` uses the handoff gradient values in its own
  constant; `AuthShell`'s near-but-different gradient is left alone (a wholesale unify would shift the auth
  screens — out of scope, per the design review).
- **Lockup reuses the centralized wordmark** (`uiText.authShell.wordmark` / `wordmarkSub`) — no new
  "Flux" literals, so RD-005's pending rename stays single-source.
- **Transform-only entrance.** `useRise` never touches opacity, so a throttled/background tab can't strand
  content hidden; reduced-motion users skip it entirely.
- **Router-hook-free shell** so it can render from the top-level class error boundary in B3.

### Validation
Test (5 cases) passes. Full `npm run check` green — 385 tests (+5). Not browser-verified: nothing renders
these yet; on-screen fidelity is checked when the pages land (B3–B5).

### Repo decisions impact
**No new repo decision.** Honors **RD-005** (wordmark single-source via `uiText`). RD-004/006 not touched.

---

## Phase B2 — StatusScreen renderer (2026-06-04)

### Goal
The Direction-B body renderer that the three status pages pass content into: ghost-glyph watermark + centred
stack (pill eyebrow · title · body · helper · console strip · actions).

### What landed
- `app/status/StatusScreen.tsx` — `StatusScreen(content)` + the `StatusContent` shape (tone, glyph, copy,
  meta strip, optional helper/watermark/primary/secondary/extra).
- `src/test/status-screen.test.tsx` — 5 cases.

### Meaningful decisions
- **Tone = colour + a word.** `TONES` maps bad/warn/brand → `{ fg, bg }` token vars; the eyebrow always
  carries a word, never a bare colour (locked UX rule).
- **Secondary action is polymorphic:** `href` → a plain `<a>` (router-hook-free, so the error fallback can use
  it outside the router); `onClick` only → a `<button>` (e.g. `history.back()`). Primary is the brand `Button`.
- **Watermark is overridable** — pages pass the "404" numerals; otherwise it's the glyph at 372px / stroke 1.1.
- **Glyph sizing via `className`** (`h-[372px]`, `h-3.5`) relies on `cn`'s `twMerge` to override the icon
  wrappers' default `h-4 w-4` — verified `cn` uses tailwind-merge.
- `useRise` animates the foreground stack; the watermark stays put (`aria-hidden`).

### Validation
Test (5 cases) passes — copy/strip render, primary fires, secondary renders as link vs button, watermark
override. Full `npm run check` green — 390 tests (+5). Not browser-verified: nothing renders it until the
pages land (B3–B5).

### Repo decisions impact
**No new repo decision.** Presentational renderer; no Accepted decision touched. (Router-hook-free design is
what lets B3 reuse it from the top-level error boundary.)

---

## Phase B3 — AppErrorFallback + error threading (2026-06-04)

### Goal
Rebuild the error fallback on the status family (bad tone), add a dev-only collapsible stack, and actually
thread the caught error through both boundaries — which they did not do before.

### What landed
- `app/AppErrorFallback.tsx` — rebuilt on `FullPageStatus` + `StatusScreen` (bad tone): Reload (primary),
  plain `<a>` to the dashboard (secondary), dev-only `ErrorDetails` as `extra`. Signature `({ error? })`,
  **no router hooks**.
- `app/status/ErrorDetails.tsx` — collapsed-by-default disclosure; renders `error.message` + `error.stack`;
  gated to `import.meta.env.DEV`.
- `app/RouteErrorBoundary.tsx` — now passes `useRouteError()` to the fallback.
- `app/AppErrorBoundary.tsx` — state changed `hasError → error`; passes the caught error to the fallback.
- `uiText.appError` — added `eyebrow` / `strip` / `details`.
- `src/test/app-error-boundary.test.tsx` — 2 cases (router-free render incl. plain dashboard link; dev-only
  stack reveals the thrown error on expand).

### Meaningful decisions
- **Error is now threaded.** Both boundaries previously discarded the caught error; they now pass it so
  `ErrorDetails` can show it. The fallback takes `error?: unknown` (route errors aren't always `Error`).
- **Dev-only stack.** `ErrorDetails` renders only under `import.meta.env.DEV` — a stack trace must never reach
  production users.
- **Still router-hook-free.** The dashboard action is `StatusScreen`'s `href` secondary → a plain `<a>`, so
  the fallback renders correctly from the class boundary outside RouterProvider (existing regression test kept).

### Validation
Test (2 cases) passes. Full `npm run check` green — 391 tests. Not browser-verified directly (the error
fallback can't be triggered live without crashing the app); its `FullPageStatus` + `StatusScreen` visual is
verified live at B4 via the 404, which renders the identical family.

### Repo decisions impact
**No new repo decision.** Local UI rebuild; the dev-only stack honors the "never expose internals in prod"
posture but introduces no repo-wide decision. RD-004/005/006 not touched.

---

## Phase B4 — NotFoundPage (2026-06-04)

### Goal
Rebuild the 404 on the status family (brand tone), with the real attempted path in the console strip and both
standalone and in-shell placements. First **live**-verified status screen.

### What landed
- `app/NotFoundPage.tsx` — rebuilt on `FullPageStatus` + `StatusScreen` (brand tone, compass glyph,
  **"404" numeral watermark**); console strip `GET <pathname> · 404` from `useLocation`; primary
  "Go to dashboard" (`navigate(routes.dashboard)`), secondary "Back" (chevron-left, `navigate(-1)`).
  `inShell` prop drops the gradient/lockup.
- `app/router.tsx` — the in-shell `*` route now renders `<NotFoundPage inShell />`; the top-level `*` stays
  standalone.
- `uiText.notFound` — `eyebrow` → "404 · Not found", added `back`.
- `src/test/app-routing.test.tsx` — added an assertion that the strip surfaces the real attempted path.

### Meaningful decisions
- **Router hooks are fine here.** Unlike `AppErrorFallback`, `NotFoundPage` always renders inside
  RouterProvider, so it uses `useLocation`/`useNavigate` (real path in the strip, client-side nav).
- **One renderer, two placements.** Both variants use the same `StatusScreen`; `inShell` only drops the
  shell's gradient + lockup (the four-region shell already provides chrome).

### Validation
Routing test (5 cases) passes. Full `npm run check` green — 391 tests. **Browser-verified live** (dev,
authenticated, dark mode), no console errors:
- **Standalone** (`/totally-unknown-page`): `FullPageStatus` gradient + `BrandLockup` wordmark, the "404"
  ghost watermark, strip `GET /totally-unknown-page · 404`, Dashboard + Back actions.
- **In-shell** (`/admin-ui/does-not-exist`): renders inside the shell (rail still visible), no gradient/lockup,
  strip `GET /admin-ui/does-not-exist · 404`, "404" watermark.

This also transitively confirms B1 (`FullPageStatus`) and B2 (`StatusScreen`) render correctly live.

### Repo decisions impact
**No new repo decision.** Local UI rebuild; RD-004/005/006 not touched (RD-005 wordmark reused via BrandLockup).

---

## Phase B5 — SessionDisabledPage (2026-06-05)

### Goal
Rebuild the session-disabled screen on the status family (warn tone) and, per **Decision D**, promote it to a
**full-page** route — out of `AppShell`, on the gradient — since the session guard turns off all console
access and there is nothing actionable inside the shell chrome.

### What landed
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

### Meaningful decisions
- **Full-page, route out of `AppShell` (Decision D).** A disabled session has no usable console, so showing
  rail/panel/inspector would imply controls that do not work. The page stands alone on the gradient.
- **No primary action (handoff).** There may be nothing the user can do; the helper line carries the real
  guidance ("ask a workspace administrator"). Only a legitimate `Return to sign in` link.
- **Plain `<a href>` for the return link.** Leaving a disabled session is a fresh load, not in-app nav.

### Validation
`session-disabled` (2) + `shell-regions` (4) pass. Full `npm run check` green — **394 tests**.
**Browser-verified live** (dev, dark mode), no console errors:
- Direct `/admin-ui/session-disabled`: lock watermark + `BrandLockup` wordmark on the gradient, eyebrow
  "Restricted", title "Admin access is disabled", body, emphasized "workspace administrator" helper, strip
  `GUARD · admin-ui access disabled`, `Return to sign in` → `/admin-ui/login`, **no** primary button.
- Guard redirect: `admin-ui-enabled=false` + visiting `/admin-ui/webhooks` redirects to
  `/admin-ui/session-disabled`, full-page (**no shell rail**).

### Repo decisions impact
**No new repo decision.** Implements the already-recorded **Decision D** (latest handoff: full-page, route out
of `AppShell`). RD-004/005/006 untouched (RD-005 wordmark reused via `BrandLockup`).
