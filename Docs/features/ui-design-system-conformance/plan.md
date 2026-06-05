# Implementation Plan — Settings + Status Screens

See [`research.md`](./research.md) for specs/findings, [`phases.md`](./phases.md) for the tracker.
Frontend-only. **Part A = Settings page. Part B = Status screens.** They can be built in either order; A and
B are independent.

---

# Part A — Settings page

## Architecture
- **`SettingsPort { getConfig(): SettingsConfig }`** for now (read only, real). `updateConfig(...)` documented
  in the port file as the **future drop-in** for the write API.
- **`HttpSettingsAdapter.getConfig`** calls the live `GET /admin/settings/config` and projects it to a display
  `SettingsConfig`; the 3 unbacked flags + the managed-webhooks list fill **one `// MOCK:`-fenced block**.
- **Read flow:** `SettingsPage` → `useSettingsConfigQuery` → `useAppPorts().settingsPort.getConfig()` →
  adapter → `HttpJsonClient.get('/admin/settings/config', schema)`.
- **Editing gate:** a single `useComingSoon()` helper (wraps `notify.info("This editing feature is coming
  soon.")`) wired to every control's change handler + Save/Sync. Controls show real values; interaction
  doesn't mutate/persist. Secrets render read-only as "Configured" / "Not configured".

## Phases (A)
- **A1 — Foundation (`modules/settings/lib`):** Zod schema for the GET response; display `SettingsConfig`
  type; `SETTINGS_SECTIONS` metadata (control kinds + dotted keys; copy from `uiText`); projector
  (response → display values, mock-fenced fields). Unit-tested.
- **A2 — Platform seam:** `platform/ports/settingsPort.ts` (getConfig; documented updateConfig seam);
  `platform/adapters/http/httpSettingsAdapter.ts` (real GET + mock-fenced fill); register in `container.ts`;
  `queryKeys.settings.config`.
- **A3 — Data hook:** `modules/settings/data/useSettingsConfigQuery.ts`.
- **A4 — Primitive + icons:** `shared/ui/Toggle.tsx` (role=switch, keyboard, token-driven) + barrel;
  `SettingsIcon` (rail) + `RefreshIcon` ("Sync now") in `icons.tsx` + barrel.
- **A5 — UI (`modules/settings/ui`):** `SettingRow` (toggle/number/select/text + read-only secret status),
  `ManagedWebhooksCard` (read-only `DataTable` + "Sync now"), `SettingsPage` (Panel "Configuration"
  section-nav via `useShellRegionRegistration`; coming-soon gate on all edit/Save/Sync). `max-w-[720px]`.
- **A6 — Wire-in:** `routes.ts` (`AppNavKey += 'settings'`, `routes.settings`, `appNavItems`), `router.tsx`
  (route under `AuthGuard`), `AppRail.tsx` (`NAV_ICONS.settings`), `uiText.ts`
  (`settings` namespace + `nav.settings` + `comingSoon`).
- **A7 — Tests + gates:** `src/test/settings-projection.test.ts` + `src/test/settings-page.test.tsx`
  (renders real values; section-nav switch; interacting fires coming-soon; managed table; loading/error).

### Files (A)
**New:** `modules/settings/lib/{settingsSchemas,settingsSections,settingsProjection}.ts`;
`modules/settings/data/useSettingsConfigQuery.ts`;
`modules/settings/ui/{SettingsPage,SettingRow,ManagedWebhooksCard}.tsx`;
`platform/ports/settingsPort.ts`; `platform/adapters/http/httpSettingsAdapter.ts`; `shared/ui/Toggle.tsx`;
`src/test/{settings-projection.test.ts,settings-page.test.tsx}`.
**Edited:** `platform/container.ts`, `platform/query/queryKeys.ts`, `shared/constants/routes.ts`,
`app/router.tsx`, `shared/ui/AppRail.tsx`, `shared/ui/icons.tsx`, `shared/ui/index.ts`,
`shared/constants/uiText.ts`.

---

# Part B — Status screens

## Architecture
- **One shared shell + one renderer.** `FullPageStatus` (gradient + lockup + centered slot,
  `isolation: isolate`) wraps `StatusScreen` (Direction B: watermark + pill eyebrow + title + body + helper +
  console strip + actions + extra). The 3 pages = thin wrappers passing tone + copy + actions.
- **Placement:** co-locate shell/renderer in `src/app/status/` (consumers are all in `src/app`; must stay
  router-hook-free for `AppErrorFallback`). Glyphs → shared `icons.tsx`; `useRise` → shared hook.
- **Gradient:** the status family gets its **own** constant using the **handoff** values (which differ from
  `AuthShell`'s and add a `var(--color-bg)` base). Do **not** silently refactor `AuthShell` onto it — that
  would shift the auth screens; any unification is a separate, deliberate visual change.

## Phases (B)
- **B1 — Shared foundation:** `src/app/status/FullPageStatus.tsx` (incl. its own handoff-value gradient
  constant; leave `AuthShell` untouched); `shared/lib/useRise.ts` (transform-only WAAPI, reduced-motion
  guard); glyphs in `icons.tsx` +
  barrel (`AlertTriangleIcon`, `CompassIcon`, `LockIcon`, `ChevronLeftIcon`, `ChevronDownIcon`).
- **B2 — Content renderer:** `src/app/status/StatusScreen.tsx` (tone map, `PillEyebrow`, `StatusStrip`, quiet
  link, `Actions`, watermark, `useRise`).
- **B3 — `AppErrorFallback` + error threading:** rebuild as `({ error? })` using `FullPageStatus`+`StatusScreen`
  (bad tone); Reload + plain `<a>`; dev-only `ErrorDetails` (`import.meta.env.DEV`). Thread error from
  `RouteErrorBoundary` (`useRouteError()`) + `AppErrorBoundary` (state). No router hooks in the fallback.
- **B4 — `NotFoundPage`:** brand tone, compass + "404" watermark; strip uses real path (`useLocation`);
  primary router link → `/admin-ui`, secondary Back (`history.back()`); standalone + `inShell` (wire the
  in-shell `*` route to pass `inShell`).
- **B5 — `SessionDisabledPage`:** warn tone, lock; helper with emphasized "workspace administrator"; no
  primary, quiet "Return to sign in"; render full-page `FullPageStatus` (resolve shell-vs-content placement).
- **B6 — Copy:** extend `uiText` (`appError` / `notFound` / `session`) with eyebrows, titles, bodies, strip
  texts, helper, action + error-details labels.
- **B7 — Tests + gates:** `src/test/status-screens.test.tsx` (per-page tone/copy/actions; `AppErrorFallback`
  renders without a router + `ErrorDetails` only in DEV; 404 standalone vs `inShell`; session has no primary).

### Files (B)
**New:** `src/app/status/{FullPageStatus,StatusScreen}.tsx`; `shared/lib/useRise.ts`; a shared gradient
constant module; `src/test/status-screens.test.tsx`.
**Edited:** `src/app/{AppErrorFallback,NotFoundPage,SessionDisabledPage,RouteErrorBoundary,AppErrorBoundary}.tsx`;
`src/app/router.tsx`; `shared/ui/icons.tsx` + `shared/ui/index.ts`; `shared/constants/uiText.ts`.

---

## Verification (both parts)
- `cd ui && npm run check` → lint + lint:tokens (no hex) + knip (no dead exports) + build + test all green.
- New Vitest specs pass (Settings projection + page; status-screens incl. router-free `AppErrorFallback`).
- **Browser (preview MCP):**
  - **Settings** — `/admin-ui/settings` (auth required): section-nav switches; real values render; changing a
    control fires "This editing feature is coming soon."; managed table + "Sync now"; dark-mode flip; rail icon active.
  - **Status** — trigger a thrown error; visit an unknown top-level URL (standalone 404) and an unknown
    `/admin-ui/*` (in-shell 404); `/admin-ui/session-disabled`. Verify gradient, ghost-glyph watermark,
    console strip, lockup, entrance rise, dark mode. Screenshots.

## Future (unblocks Settings editing) — see README "Out of scope"
When the backend write path lands: add `SettingsPort.updateConfig`, `useUpdateSettingsMutation`, the design's
Save/Reset dirty bar + "Settings saved" toast, and remove the coming-soon gate.
