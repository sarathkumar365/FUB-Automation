# Reporting Phase 1 (Frontend) — Operations Dashboard UI

> **Status: implementation plan — not yet built. Depends on the backend slice.** Build
> [phase-1-backend-implementation.md](./phase-1-backend-implementation.md) first; this doc **consumes
> the `DashboardSnapshotDto` contract** it produces. A **presentation + data-layer rewrite** of the
> existing dashboard module — no new module, evolve in place.
>
> Design source of truth: `ui/Flux Design System/design_handoff_dashboard/` (Direction A) — but see
> "Heads-up on the prototype": the prototype draws only the happy path; this doc is the v1 truth.
> UI rules: `ui/AGENTS.md` (module boundaries, tiers, tokens, RD-011).

## Changelog

- **2026-06-24 — split BE/FE.** Carved out of the combined `phase-1-implementation.md` (rev 4). The
  backend doc owns the contract; this doc owns its consumption + the UI. Content unchanged.

## Goal

Replace the current dashboard data layer (4 client-side list calls stitched with
`systemHealth.mode='placeholder'`) with **one call to the snapshot endpoint**, and rebuild the page
to the handoff design (3-state hero, throughput chart, volume funnel, recent runs, needs-attention).

## The existing module — evolve in place, do NOT restart

`ui/src/modules/dashboard/` already follows the canonical layout (`data/` + `lib/` + `ui/`) and is
wired correctly (ports via `useAppPorts`, TanStack Query + `queryKeys.dashboard.snapshot()`,
`shared/ui` primitives, `uiText`/`routes` constants, tokens-not-hex, aliases, shell Panel/Inspector
registration, Loading/Error/empty states). The only non-final part is the **placeholder data path**.
Phase 1 rewrites **two layers** and keeps the rest:

- **Rewrite:** the data layer (placeholder → real snapshot via port/adapter/contract) and the
  presentation (rebuild to the handoff + two chart components).
- **Keep:** the folder skeleton, shell registration, Loading/Error/empty patterns, navigation,
  `uiText`/`routes`/tokens, the `queryKeys.dashboard.snapshot()` key.
- **Retire:** `lib/dashboardSnapshot.ts`'s `buildDashboardSnapshot` + `mode:'placeholder'` type.

## The contract you consume

The shape returned by `GET /admin/dashboard/snapshot` (field semantics + calculations are in the
[backend doc](./phase-1-backend-implementation.md#dashboardsnapshotdto-the-contract)):

```jsonc
DashboardSnapshotDto {
  window: { from, to, label },
  hero:   { state: "HEALTHY"|"DEGRADED"|"UNHEALTHY", openFailures, stats: { runs, successRate, openFailures } },
  throughput: { series: int[24], peak, avg, perMin },
  funnel: { ingested, domainEvents, runs, failed },        // four volume counts — no conversion %s
  recentRuns: RunRow[5],
  needsAttention: FailureRow[]                              // ≤ 50
}
Delta { value: float|null, direction: "UP"|"DOWN"|"FLAT" }   Stage { value, spark: int[8] }
RunRow { id, workflowKey, status, startedAt, completedAt, durationSec }
FailureRow { ref, workflowKey, status, reason, ageSeconds }
```

Nullable rules: `successRate.value` and any `Delta.value` can be `null` (render `—`); `reason` can be
`null` (render "—"/"Unknown"); `completedAt`/`durationSec` `null` for non-terminal runs.

## Data layer (port/adapter/contract — RD-011, not a direct fetch)

- Add the Zod schema + inferred `DashboardSnapshot` type in `platform/contracts/dashboardSchemas.ts`;
  a `dashboardPort.getSnapshot()` port (`platform/ports/dashboardPort.ts`); an HTTP adapter
  (`platform/adapters/http/httpDashboardAdapter.ts`) that GETs `/admin/dashboard/snapshot` via
  `httpJsonClient` and Zod-validates the body; register it in `platform/container.ts`.
- Rewrite the **existing** `modules/dashboard/data/useDashboardSnapshotQuery.ts` to call the port
  **once** (drop the 4-call `Promise.all` + `buildDashboardSnapshot` stitching), keeping
  `queryKeys.dashboard.snapshot()` and **no `refetchInterval`** (event-driven; see "Liveness & refresh").
- **Retire the placeholder** `lib/dashboardSnapshot.ts` (`mode:'placeholder'`, `buildDashboardSnapshot`,
  the old `DashboardSnapshot` type). The type now comes from `platform/contracts`; `lib/` keeps only
  pure UI-derive helpers (delta polarity, subtitle composition, the all-zero chart guard).

## Chart components

- Two new SVG chart components: **AreaChart** (throughput) and **Sparkbars** (funnel) — thin,
  token-driven, **custom (no chart lib)** per
  [RD-013](../../repo-decisions/RD-013-reporting-charts-custom-vs-library.md). These are a **port**
  of `charts.jsx` (`AreaChart` + `Sparkbars`), JSX→TSX, inline-styles→tokens. Keep `smoothPath`
  (Catmull-Rom), `useId()` for unique gradient ids, `preserveAspectRatio="none"` +
  `vectorEffect="non-scaling-stroke"`, and guard the all-zero/flat series. Dashboard-local for now;
  promote to `shared/ui` on a second consumer.

## Page composition

- Recreate hero / funnel rail / recent-runs / needs-attention per the handoff, reusing existing
  `shared/ui/*` (Button, Badge, DataTable) + toast via `shared/notifications` (`NotifyProvider`, not
  `shared/ui`). Pulse dot = CSS. **No replay/confirm flow** in v1 (the worklist is view-only).
- A small **"updated {time}"** label (from `window.to`) so the point-in-time figures read honestly
  under the always-pulsing dot.

> **Heads-up on the prototype:** `design_handoff_dashboard/prototype/` only ever draws the *happy
> path* — 2 health states (`All clear`/`Healthy`), all deltas green-up, a dev-only 12h/24h tweak, and
> no empty/error states. The v1 contract differs on all four; the items below are the source of
> truth, not the prototype.

### FE contract details (resolved against code + handoff)

**Reuse — these are already built, do not re-invent:**
- **Status → badge:** use `getWorkflowRunStatusTone()` (`modules/workflow-runs/lib/workflowRunsDisplay.ts`)
  + `<StatusBadge>` (`shared/ui`). Mapping is complete: `PENDING/BLOCKED → warning`,
  `DUPLICATE_IGNORED/CANCELED → info`, `COMPLETED → success`, `FAILED → error`.
- **Row navigation:** `recentRuns` and `needsAttention` rows → `navigate(routes.workflowRunDetail(id))`
  (`shared/constants/routes.ts` → `/admin-ui/workflow-runs/:id`); the detail page already exists.
- **Loading / error:** reuse `<LoadingState>` and `<ErrorState onRetry>` exactly as the current
  `DashboardPage` does (`isPending` / `isError` branches) — this covers the "DB error → 500" case.
- **Tokens:** all CSS vars live in `colors_and_type.css` (incl. dark-theme). Chart port uses
  `--color-brand` (stroke), `--color-brand-soft` (gradient), `--color-status-ok/warn/bad`,
  `--color-text-muted` (grid), `--color-border` (baseline).

**Delta direction coloring (the DTO sends sign only; the UI applies per-stat polarity):**
- `successRate` — higher is better → `UP = ok/green`, `DOWN = bad/red`.
- `openFailures` — higher is worse → `UP = bad/red`, `DOWN = ok/green`.
- `runs` — volume is amoral → **always muted/neutral**, arrow only, no good/bad color.
- `FLAT` or `null` (zero baseline) → muted, no arrow, value renders `—`.
- (The prototype hardcodes `deltaTone="ok"` on all three — replace with this polarity map.)

**Health headline — 3 states (word + token), subtitle composed from stats:**
- `HEALTHY → "Healthy"` (`--color-status-ok`) · `DEGRADED → "Degraded"` (`--color-status-warn`) ·
  `UNHEALTHY → "Unhealthy"` (`--color-status-bad`).
- Subtitle: `HEALTHY` with 0 runs → "No runs in the last 24h."; `HEALTHY` with runs → "No failures
  need attention."; `DEGRADED`/`UNHEALTHY` → "{openFailures} of {runs} runs failed in the last 24h."
  **Drop the prototype's "waiting on replay" wording** — there is no replay in v1.

**Empty / cold-start visuals:** `needsAttention` empty → a muted "Nothing needs attention." line
(not an empty table); `recentRuns` empty → existing muted message; charts guard the all-zero series
(flat baseline, no NaN path); hero still reads green `Healthy` with the "No runs" subtitle.

**No range toggle in v1** — the throughput chart always shows the full 24h (24 points). The
prototype's dev-only 12h/24h tweak is not promoted to the UI.

### Design coverage (handoff → v1)

Every handoff section maps onto the contract. **Deliberate divergences from the prototype** —
do *not* "recreate pixel-faithfully" on these; the prototype predates the v1 decisions:
- **Headline:** prototype 2-state (`Healthy`/`All clear` on failure count) → v1 **3-state on success
  rate** (`HEALTHY`/`DEGRADED`/`UNHEALTHY`).
- **Replay:** prototype's Replay button + ConfirmDialog + Toast + live recompute → v1 is **view-only**
  (rows navigate to run detail); the entire replay interaction is removed.
- **events/min:** prototype live-ticks a random value every 2.6s → v1 `perMin` is a **real snapshot
  number** frozen at fetch; the "live" feel is the CSS pulse dot only (see "Liveness & refresh").
- **Tweaks panel** (accent / conversion-toggle / range / live-toggle) is **dev-only — not shipped**.
  Conversion %s are **removed entirely** in v1; range is fixed at 24h.
- **`needsAttention` row:** RUN/CALL kind chip and `retries` line **dropped** (runs-only) — the v1
  second line is `{workflowKey} · {ageSeconds → "…ago"}` only.

**Presentational details to carry over faithfully** (from the handoff, beyond the data contract):
- Hero: ambient brand-wash blob; **"Pipeline live"** pill with the pulsing `--color-live` dot.
- Throughput chart: Catmull-Rom line, brand vertical-gradient fill, 1px baseline, hollow end-point
  marker; footer `peak · avg · now`.
- Funnel: **one** bordered surface (not 4 cards); connector chevrons indicate flow between the four
  stage counts (**no conversion % captions** in v1 — see the backend funnel note); red dot on the
  Failed stage.
- Recent Runs: the kit **DataTable "ledger"** treatment (tracked uppercase header, hairline row rules,
  4px leading status-accent rail, clickable rows) — **replace the page's current hand-rolled `RunList`**.
- Type/tokens: Manrope UI + JetBrains Mono for all numbers/ids/timestamps (`tabular-nums`); every color
  via tokens (light + dark).
- Motion/responsive: entrance is **not** an opacity fade; honor `prefers-reduced-motion`; the 2-col hero
  and runs/attention grids collapse to single column below ~960px.

## Liveness & refresh (v1)

**v1 does not poll.** The snapshot is refreshed by **events, not a timer**:

- **on mount** — fetched when the dashboard opens;
- **on window focus** — `refetchOnWindowFocus` (TanStack default, kept on) refetches when the
  operator tabs back.

No `refetchInterval`. The **"live" feeling is the CSS pulse dot** (continuous, no data behind it),
not the number's refresh rate. `events/min` (`throughput.perMin`) is a plain field in the snapshot,
frozen at fetch time and refreshed on the same events — honest because of the "updated {time}"
label. (In v1 there is no replay-driven invalidation, since the dashboard performs no actions.)

**Going live later is a deferred, isolated change — no machinery built now.** The
single-snapshot-object boundary behind one hook *is* the switch: time-based liveness → add
`refetchInterval` (one line); true push → swap the hook's source to an SSE subscription emitting
snapshots (UI unchanged; SSE infra already exists via `WebhookSseHub` / `SseEmitter`); independent fast
pulse → promote `perMin` to its own `GET /admin/dashboard/pulse`. The trigger to do any of these is an
**access-pattern divergence** that does not exist in v1.

## Order of work (frontend sub-steps)

> Prereq: the backend snapshot endpoint is live and returns the contract above.

1. **Data layer** — `platform/contracts/dashboardSchemas.ts` (Zod + type), `dashboardPort` +
   `httpDashboardAdapter` + `container.ts` registration; rewrite `useDashboardSnapshotQuery` to one
   call; retire the placeholder lib. (Tests: adapter Zod-validation; hook query.)
2. **Presentation** — hero (3-state headline + per-stat delta polarity), volume funnel, recent runs
   (DataTable ledger), needs-attention (view-only); AreaChart + Sparkbars. (Tests: per `ui/AGENTS.md`
   — at least one new test per component/behavior; `npm run check` green.)

## Validation criteria

- Dashboard renders the handoff design with real snapshot data; one network call, not four.
- `hero.state` styles correctly across HEALTHY / DEGRADED / UNHEALTHY (incl. no-runs → green Healthy).
- Delta arrows colour per the polarity map (failures-up = red, success-up = green, runs = neutral);
  null/flat render `—`.
- Nullable fields render `—`/"Unknown", never `0`/`NaN`.
- `events/min` shows a real value; snapshot refreshes on mount / window-focus (no timer), with an
  "updated {time}" label and a continuous CSS pulse dot.
- `recentRuns` and `needsAttention` rows navigate to the run detail; the worklist is view-only (no
  replay control).
- Per `ui/AGENTS.md`: `npm run lint`, `npm run build`, `npm run test` pass; new tests added for the
  new components/hook.

## Linked

- Backend slice (contract source): [phase-1-backend-implementation.md](./phase-1-backend-implementation.md)
- [plan.md](./plan.md) (Phase 1) · [RD-011](../../repo-decisions/RD-011-ui-schema-ownership.md) (UI schema ownership)
  · [RD-013](../../repo-decisions/RD-013-reporting-charts-custom-vs-library.md) (custom charts).
