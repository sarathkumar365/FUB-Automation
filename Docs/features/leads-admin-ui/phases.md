# Leads Admin UI — Phases

**Scope:** `/admin-ui/leads` list + detail page (modern UX), backed by a new
read API over the existing `leads` table and cross-linked history from
`processed_calls`, `workflow_runs`, `webhook_events`.
**Supersedes the UX sections of:** [plan.md](./plan.md). Data shape + endpoint
contracts in that doc still hold. This tracker overrides the "modal detail"
and "three separate recent sections" choices (see `plan.md` supersession banner).
**Baseline:** 2026-04-21, branch `feature/workflow-builder-storyboard` (per
user directive 2026-04-21; AGENTS.md branch-strategy deviation explicitly
accepted for this slice).

**Phase structure:** Single Phase 1 with four internal slices (A–D). Progress
logged in [`phase-1-implementation.md`](./phase-1-implementation.md).

## Legend

- Status: `[ ]` todo · `[~]` in progress · `[x]` done · `[-]` dropped (with reason)
- Severity: **H** high-impact · **M** medium · **L** polish
- Gate per slice (FE): `cd ui && npm run lint && npm run build && npm run test`
- Gate at feature end (BE): `./mvnw test` (per user directive 2026-04-21 — backend suite runs once at the end, not per slice)

---

## Motivation

The `leads` table (V14) is live and populated by `LeadUpsertService` on every
FUB person webhook. There's no way to *look at* a lead yet — no list, no
detail, no timeline. Operators triaging "what happened to this lead?" have
to query Postgres directly and stitch together processed-calls + workflow-runs
+ webhook-events by `source_lead_id` in their head.

The old [plan.md](./plan.md) specified a list + modal detail. We're upgrading
the detail surface to a dedicated route with a unified chronological timeline
(modern operator-console pattern — cf. Sentry issue detail, HubSpot contact
timeline, Linear issue activity).

---

## Locked decisions (2026-04-21)

| id | question | decision |
| --- | --- | --- |
| **D-Lead.1** | Detail surface: route or modal? | **Route** — `/admin-ui/leads/:sourceLeadId`. Bookmarkable, shareable, supports `backTo`, lets workflow-run detail link directly to the lead it fired for. |
| **D-Lead.2** | Recent activity: three parallel lists or unified timeline? | **Unified timeline** with filter chips (`All / Calls / Workflows / Webhooks`). One chronological stream answers "what happened?" without user-side time-stitching. |

---

## Pending decisions — "go with defaults" unless flagged

### Slice A — Backend read API

| id | question | default |
| --- | --- | --- |
| A1 | Paging shape for list | cursor keyset on `(updated_at DESC, id DESC)` — matches existing webhooks pattern. |
| A2 | Search on list | exact / starts-with on `sourceLeadId` only in v1. No name/email search yet. |
| A3 | List filters | `source` (default FUB), `status`, `from`, `to`. |
| A4 | Detail endpoint live-refresh default | `includeLive=true`. On FUB failure: HTTP 200 + `liveStatus=LIVE_FAILED` + local data. |
| A5 | Timeline window | top 20 combined activity events, not top 10 per stream — because UI merges them. Raw per-stream top-10 still returned as arrays so filter chips can page independently later. |
| A6 | Raw `lead_details` JSON in detail response | include as `snapshot` block. UI exposes it as collapsible "raw snapshot". |

### Slice B — Frontend list page

| id | question | default |
| --- | --- | --- |
| B1 | Row click | navigate to `/admin-ui/leads/:sourceLeadId?backTo=<current-list-url>`. |
| B2 | Column set | name (from snapshot) · sourceLeadId · stage · assignedTo · status · lastSyncedAt · updatedAt. |
| B3 | Name missing fallback | show `(no name)` in muted text; keep sourceLeadId prominent. |
| B4 | Status chip | `ACTIVE` / `ARCHIVED` / `MERGED` via existing `StatusBadge` primitive. |
| B5 | Empty state | "No leads yet. Leads appear here after the first FUB person webhook is processed." |
| B6 | Pagination | next-only cursor (matches plan A1); no page numbers. |

### Slice C — Frontend detail page

| id | question | default |
| --- | --- | --- |
| C1 | Hero content | status dot · full name · stage · assigned-to · primary phone · primary email · tags · `[Refresh from FUB]` top-right. |
| C2 | Rail (shell inspector) sections | **Identifiers** (sourceLeadId, sourceSystem, internal id) · **Timestamps** (created, updated, last synced) · **Live refresh** (status + fallback banner when LIVE_FAILED). |
| C3 | Timeline row shape | icon (📞/⚙️/🪝) · relative time · one-line summary · click → deep-link to that record (call detail, workflow run, webhook event). |
| C4 | Timeline tabs | chip bar: `All (default) / Calls / Workflows / Webhooks`. Filter client-side over the already-fetched events. |
| C5 | Raw snapshot | `<details>` at the bottom of the main column. Monospace JSON. |
| C6 | Refresh-from-FUB button | re-fires `GET /admin/leads/:id/summary?includeLive=true` with cache bust; the summary endpoint is the single source of truth for live refresh. No new mutation endpoint. On `liveStatus=LIVE_FAILED` the rail renders the fallback banner. |
| C7 | No-snapshot-data state | if `leadDetails` is empty (shouldn't happen but guard), show "No snapshot data — try Refresh from FUB". |

### Slice D — Navigation + cross-linking

| id | question | default |
| --- | --- | --- |
| D1 | Sidebar entry position | between `Processed Calls` and `Workflows`. Label: `Leads`. Rail abbrev: `LD`. |
| D2 | Link from workflow run detail | replace the raw `sourceLeadId` text with a NavLink to `/admin-ui/leads/:sourceLeadId` when present. |
| D3 | Link from processed call detail | same — link `sourceLeadId` to the lead detail page. |
| D4 | Link from webhook event detail | same. |

---

## Slice A — Backend read API

**Goal:** `GET /admin/leads` (list) + `GET /admin/leads/:sourceLeadId/summary` (detail). No separate refresh endpoint — the summary endpoint's `includeLive` param doubles as the live-refresh trigger (decision 2026-04-21, Q3).

| id | status | task |
| --- | --- | --- |
| A-1 | [ ] | DTOs: `LeadListItem`, `LeadListResponse`, `LeadSummaryResponse`, `ActivityTimelineEvent` (union-ish: kind + payload). |
| A-2 | [ ] | `LeadFeedReadRepository` (JDBC keyset) — list + cursor codec. Reuse webhook-feed cursor helper or duplicate with different key names per plan §2. |
| A-3 | [ ] | Extend `ProcessedCallsRepository`, `WorkflowRunsRepository`, `WebhookEventsRepository` with `findTopNBySourceLeadId` (top 10 each). |
| A-4 | [ ] | `LeadAdminQueryService` — orchestrate list, summary aggregation, merge the three recent streams into one sorted-desc `activity[]` (top 20) alongside per-stream arrays. |
| A-5 | [ ] | `LeadAdminController` endpoints — list, summary. Summary's `includeLive=true` path re-invokes `LeadUpsertService.upsertFubPerson` via `FollowUpBossClient.getPersonRawById` (so "refresh" = re-fetch summary with includeLive, no new endpoint). |
| A-6 | [ ] | Controller + service + repo tests per plan §Test Plan. Include: cursor stability, top-N bounds, live-fail fallback, unified-activity ordering under mixed timestamps. |
| A-7 | [ ] | Integration test: seed lead + one call + one run + one webhook event; assert unified timeline ordering. |

## Slice B — Frontend list page

**Goal:** route, nav entry, table, filters, cursor.

| id | status | task |
| --- | --- | --- |
| B-1 | [ ] | `LeadsPort` interface + HTTP adapter + Zod schemas for list + summary. Add to `container.ts`. |
| B-2 | [ ] | Nav entry in `appNavItems` (`routes.leads`, label, matchPaths). Rail + panel auto-reflect via existing helper. |
| B-3 | [ ] | `useLeadsQuery`, `useLeadSummaryQuery`. Query keys: `leads.list(filters)`, `leads.summary(sourceLeadId, source, includeLive)`. Refresh = `queryClient.invalidateQueries` on the summary key with `includeLive=true`. |
| B-4 | [ ] | `lib/leadsSearchParams.ts` — URL-backed filters (source, status, from, to, leadId, cursor). |
| B-5 | [ ] | `ui/LeadsPage.tsx` — PageHeader + FilterBar + DataTable + cursor Next button. Row click → navigate to detail with backTo. |
| B-6 | [ ] | Vitest: search-params round-trip, filter apply/reset, cursor-next flow, empty-state, row-click navigation. |

## Slice C — Frontend detail page

**Goal:** route, hero, unified timeline, rail, raw snapshot, refresh button.

| id | status | task |
| --- | --- | --- |
| C-1 | [ ] | Route `/admin-ui/leads/:sourceLeadId` in `router.tsx`. backTo param parsed like WorkflowRunDetail. |
| C-2 | [ ] | `LeadDetailHero` component — C1 content, respects missing-field fallbacks. |
| C-3 | [ ] | `LeadActivityTimeline` component — chip tabs (All/Calls/Workflows/Webhooks), filter client-side, relative time formatter, per-kind icon, row click → deep link to source record. |
| C-4 | [ ] | Shell-inspector rail body — grouped identifiers + timestamps + live-refresh status + fallback banner. |
| C-5 | [ ] | `LeadRawSnapshot` component — collapsible `<details>` with monospace JSON. |
| C-6 | [ ] | `LeadDetailPage` composition — Hero + Timeline + RawSnapshot in main; rail via `useShellRegionRegistration`. |
| C-7 | [ ] | Refresh-from-FUB button in hero → `queryClient.invalidateQueries(leads.summary key, includeLive=true)` → toast on LIVE_OK / error banner on LIVE_FAILED. |
| C-8 | [ ] | Vitest: hero field fallbacks, timeline filter chips, refresh success/failure paths, rail fallback banner visibility. |

## Slice D — Cross-linking polish

**Goal:** wherever the app displays a `sourceLeadId`, make it a link.

| id | status | task |
| --- | --- | --- |
| D-1 | [ ] | WorkflowRunDetailPage: wrap `sourceLeadId` display with `NavLink` to lead detail (with backTo set to run-detail URL). |
| D-2 | [ ] | ProcessedCallsPage inspector + any processed-call detail: same. |
| D-3 | [ ] | WebhooksPage feed items displaying `sourceLeadId`: same. |
| D-4 | [ ] | Vitest: link presence + correct backTo in each caller. |

---

## Execution ordering

1. **Slice A first** (backend) — nothing in UI can render without the endpoint.
2. **Slice B** (list) — user can reach the domain.
3. **Slice C** (detail) — the main event.
4. **Slice D** (cross-linking) — makes the detail reachable from the rest of the app.

Each slice: decisions lock → code → tests → gate → commit. Keep slices committable independently (list works without detail; detail works without cross-links).

---

## Risks / open questions

- **Name rendering from JSONB** — `lead_details.name` is a FUB-computed full string. If absent, fall back to `firstName + lastName`. Both may be absent → `(no name)`. Belt-and-suspenders on the Zod schema: every snapshot field is `.optional()`.
- **Unified timeline ordering across tables** — three different timestamp columns (`call_started_at`, workflow `created_at`, webhook `received_at`). Service picks one canonical "occurred at" per event. Document the mapping in A-4.
- **Cursor codec reuse** — plan §2 suggests sharing the webhooks cursor helper. Check if the existing helper leaks webhook-specific key names; either generalize or fork with different keys. Decision deferred to A-2 implementation.
- **Live-refresh rate-limiting** — FUB API has quotas. `POST /refresh` should have basic debounce client-side (disable button for 3s after click) and server-side backoff on repeated 429s. Flag for A-5.
- **Lead status transitions** — `ARCHIVED` / `MERGED` paths aren't implemented yet (no service mutates to those). Out of scope for this tracker — filter UI still exposes them for forward compatibility.

---

## Critical files

- Backend new: `controller/AdminLeadController.java`, `service/lead/LeadAdminQueryService.java`, `persistence/repository/LeadFeedReadRepository.java`, `dto/lead/*`.
- Backend touched: `service/lead/LeadUpsertService.java` (reused unchanged), existing repositories get new `findTopN...` methods.
- Frontend new: `ui/src/modules/leads/**`, `ui/src/platform/ports/leadPort.ts`, `ui/src/platform/adapters/http/httpLeadAdapter.ts`.
- Frontend touched: `ui/src/shared/constants/routes.ts` (+ nav entry), `ui/src/shared/constants/uiText.ts`, `ui/src/app/router.tsx`, Workflow/ProcessedCall/Webhook detail pages (Slice D).

---

## Verification

- Backend per plan §Test Plan (controller + repo + service + integration). Add unified-activity-merge test.
- Frontend: adapter schema round-trip, hooks, page tests listed under B-6 / C-8. Routing test asserts `/admin-ui/leads` + `/admin-ui/leads/:sourceLeadId` reachable, nav entry present.
- Manual smoke: open a lead that has ≥1 of each activity kind and confirm unified timeline order matches per-stream DB queries.
