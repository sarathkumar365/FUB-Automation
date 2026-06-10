# Research — Reporting

Consolidated findings. The design is a **redesign of the existing dashboard** leading with pipeline health; the
real work is a **new backend reporting capability** because the health/trend data is not backed today.

---

## 1. Design spec (source of truth)

`ui/Automation Engine Design System/design_handoff_dashboard/` — **Direction A, "Health headline"**, high
fidelity, token-only (tokens already exist in `ui/src/styles/tokens.css`; dark theme remaps for free). A single
scrollable column inside the four-region shell, but **the dashboard drops the contextual panel and inspector**
— it's a full-width overview (`max-width 1240px`). Three stacked sections (18px gaps):

1. **Hero** (`surface` card, ambient brand wash). Left: kicker `PIPELINE STATUS · LAST 24H`; headline
   **"Healthy"** (open failures > 0) / **"All clear"** (0); a **Live pill** ("Pipeline live", pulsing
   `--color-live` dot); a dynamic subtitle; a **3-stat strip** — Runs `312` (+8%), Success `97.0%` (+0.3pt),
   Open failures `{N}` (red when >0, delta `−2 today` / `cleared`). Right: **throughput area chart** — 24 hourly
   points, smooth brand line + soft gradient fill + end marker; header `THROUGHPUT · EVENTS / HR` + live `{n}/min`
   readout; footer `{range} ago · peak X · avg Y · now`.
2. **Funnel rail** — ONE bordered surface (not 4 cards), 4 stage blocks + connectors. Stages:
   **Ingested** `148` (events/hr) · **Domain Events** `142` (normalized) · **Workflow Runs** `97` (started) ·
   **Failed** `{N}` (red). Each stage: dot + label, 30px mono value + sub-label, an 8-bar **sparkbar**.
   Connectors carry **conversion %**: Ingested→Events `96% NORMALIZED` (green), Events→Runs `68% TO RUNS`
   (muted), Runs→Failed `3.1% FAIL RATE` (red). The conversion math is live (replay lowers Failed + fail-rate).
3. **Recent Runs** (`PageCard` + ledger `DataTable`): Run · Workflow · Status badge · Duration (`completed−started`
   in `s`) · Completed (`HH:MM:SS`), 4px status accent rail per row, whole row → run detail; footer
   "Browse all runs". **Needs attention** (`PageCard`): failure rows on `--color-status-bad-bg`, each with a
   `RUN`/`CALL` kind chip, mono ref + reason, `{workflow} · {retries} retries · {ago} ago`, an outline
   **Replay** → ConfirmDialog → confirm → row removed + toast + hero/funnel recompute. Empty state at N=0.

**Interactions:** dark toggle (shared `ThemeToggle`); replay (ConfirmDialog → `useNotify` toast → invalidate
snapshot); run row / "Browse all runs" → runs list; a live `events/min` ticker (respects
`prefers-reduced-motion`). Entrance is **not** an opacity fade (throttled tabs freeze at frame 0) — life comes
from the pulse dot, sparkbars, ticker.

The prototype's mock shape (`prototype/dash-data.jsx`) is the data contract to recreate: `THRU_24[24]`,
`RUNS_24[24]`, `SUCCESS_24[24]`, `KPI{ingested,events,runs,failedOpen,runs24,succeeded24,failed24,running24,
canceled24,successRate,p50ms,replayedToday}`, `STAGES[4]`, `OUTCOMES[4]`, `WF_PERF[4]`, `ATTENTION[]`.

---

## 2. Data needs ↔ backend reality (the crux)

The current snapshot (`useDashboardSnapshotQuery` → `buildDashboardSnapshot`) counts client-side from list
endpoints; `systemHealth.mode === 'placeholder'`. Reconciliation:

| Design need | Backend reality | Treatment |
|---|---|---|
| **Recent Runs** (id, key, status, duration, completed) | `WorkflowRunSummary` has all of it (`startedAt`/`completedAt` → duration) | ✅ Real today |
| Open-failure count · active workflows · recent webhook count | client-side counts (in use) | ✅ Real today |
| 24h **throughput / runs/hr / success%/hr** series | no time-series endpoint | 🟡 New aggregation (feasible) |
| KPIs: success rate %, 24h outcome counts, **deltas** | not computed anywhere | 🟡 New aggregation (feasible) |
| KPI: **p50 duration** | not computed | 🟡 New aggregation (`PERCENTILE_CONT`; maybe a compound index) |
| **Funnel** counts + conversion % | 3 FK-linked tables, no aggregate | 🟡 New aggregation (3-table join) |
| **Per-workflow** throughput + success rate | not available | 🟡 New aggregation (group-by key) |
| Funnel **"normalized"** stage | `webhook_events.status` is always `RECEIVED` | 🟡 Derive from `events` (domain-events) table, NOT webhook status |
| **Replay a failed RUN** | no endpoint (only call replay) | 🔴 **Stub endpoint** (D2) |
| "{N} retries" per failure | runs: retries are per-*step*, not per-run; calls: `retry_count` stored but **not incremented on replay** (TODO in code) | 🔴 Stub/derive (D2) |
| "Replayed today" KPI | blocked by replay semantics | 🔴 Stub/omit pending decision |

---

## 3. Backend feasibility dig

**No metrics/aggregation service exists** — every admin endpoint returns paginated entities; all counting is
client-side. But the hard-looking aggregations are **low-effort with no schema changes**, because the tables are
well-indexed:

- **`workflow_runs`** (`V10`): `status` ∈ {PENDING, BLOCKED, DUPLICATE_IGNORED, CANCELED, COMPLETED, FAILED},
  `created_at` (run start), `updated_at` (completion), `workflow_key`, `domain_event_id` (FK, `V23`),
  `webhook_event_id` (FK). Index **`idx_workflow_runs_status_created_at (status, created_at)`** → hourly
  `DATE_TRUNC` buckets + `GROUP BY status`, success rate, 24h-vs-prior deltas all cheap. p50 via
  `PERCENTILE_CONT(0.5)` over `updated_at − created_at`.
- **`webhook_events`** (`V1`): `received_at`, single `status='RECEIVED'`. Count ingested/hr — but cannot
  distinguish "normalized" from status (model gap).
- **`events`** / domain events (`V22`): `event_kind`, `source_event_id` (FK→webhook_events), `created_at`,
  indexed `(event_kind, created_at)`. → the funnel's "normalized" count + the
  `webhook_events → events → workflow_runs` join (all join columns indexed).
- **`processed_calls`** (`V2`): `status` ∈ {RECEIVED, PROCESSING, SKIPPED, TASK_CREATED, FAILED},
  `failure_reason`, `retry_count`, `updated_at`, index `(status, updated_at)`. → call failures for the worklist
  (real), with reason + retry_count.
- **`workflow_run_steps`** (`V10`): per-step `status`, `retry_count`, `error_message`.

**Replay endpoints:** `POST /admin/processed-calls/{callId}/replay` is **real** (resets to RECEIVED, re-dispatches,
202). **Workflow-run replay does not exist** (runs can only be canceled). Per D2 we add a **stub**.

**Admin pattern to mirror:** controllers under `/admin/**` (e.g. `AdminWebhookController` +
`AdminWebhookService`); JDBC read-model precedent for custom aggregate SQL = `WebhookFeedReadRepository` /
`JdbcWebhookFeedReadRepository`. Auth via `@PreAuthorize` (RD-004).

---

## 4. Frontend reuse inventory

Rich reuse — only charts are greenfield.

- **Primitives (`shared/ui/`):** `DataTable` (generic `ColumnDef`, row-click, **built-in left accent rail** +
  hover — exactly the "ledger" treatment), `PageCard`, `PageHeader`, `StatusBadge`/`Badge` (success/warning/
  error/info/muted), `Button` (default/secondary/outline/ghost/destructive · sm/lg/icon), `ConfirmDialog`
  (Radix), `EmptyState`/`LoadingState`/`ErrorState`, `Toggle`, `ThemeToggle`, full Lucide icon set.
- **Notifications:** `NotifyProvider` + `useNotify()` → `success/error/warning/info(msg, {title?, durationMs?})`
  (we already reuse this — top-right toast stack).
- **Replay:** `useReplayProcessedCallMutation()` → `processedCallsPort.replayProcessedCall(callId)` →
  `POST …/processed-calls/{id}/replay`; result `'accepted'|'notFound'|'notReplayable'|'unexpectedError'`;
  invalidates the calls list. **No run-replay hook yet** (build one against the stub).
- **Display helpers:** `formatWorkflowRunStatus` / `getWorkflowRunStatusTone` / `formatWorkflowRunReasonCode`
  (`workflow-runs/lib`), `formatProcessedCallStatus` / `getProcessedCallStatusTone` (`processed-calls/lib`),
  `formatDateTime` (`shared/lib/date`).
- **Charts:** **none exist** — greenfield SVG `AreaChart` + `Sparkbars` (Catmull-Rom smooth line, gradient fill,
  baseline, end marker; 8-bar sparkbars). No chart lib installed (only dagre/radix/lucide). Handoff: hand-rolled
  SVG, no heavy lib.
- **Tokens (`styles/tokens.css`):** brand / brand-soft / brand-2, status ok/warn/bad (+ `-bg`), `--color-live`,
  surface/surface-alt/border, text/text-muted, radius sm(8)/md(12)/pill(999), shadow subtle/hover/float,
  `--font-ui` Manrope, `--font-mono` JetBrains Mono. All present, light + dark.
- **Platform seam:** `platform/ports/*` + `adapters/http/http*Adapter.ts` + `container.ts` (`appPorts`) +
  `app/useAppPorts.ts` + `platform/query/queryKeys.ts` (add `reporting`). `uiText.dashboard` copy lives at
  `shared/constants/uiText.ts`.
- **Module boundary (D4):** the reporting **data seam** (schemas, projections, hooks) lives in a new
  `modules/reporting/` so any surface can consume it; future dedicated report pages go under
  `modules/reporting/ui/<report>/`. The landing **dashboard stays in `modules/dashboard/`** and consumes the
  reporting hook. Charts go in **`shared/ui/charts/`** (shared by all report surfaces). See [`plan.md`](./plan.md)
  for the full structure.

---

## 5. Open questions (carry into the plan / phase decisions)
- **Run retry-count + "replayed today":** no per-run source. Stub in the DTO (e.g. `retries: null` / omit) or
  derive a proxy? Decide at the Part-A DTO phase; do not fabricate.
- **p50 duration:** confirm whether a `(created_at, updated_at)` index is worth adding or the 24h scan is fine.
- **Live `events/min` ticker:** the design's ticker is decorative (random in the prototype). Bind to the series'
  latest bucket or render a static "now" value rather than inventing motion. Decide in Hero phase.
