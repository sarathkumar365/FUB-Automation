# Phase 1 Implementation

## Status
In progress (2026-04-21)

## Structure
Phase 1 is delivered as four slices. Each slice is committable
independently — the list works without detail; detail works without
cross-links. Slice definitions and task tables live in `phases.md`.

| slice | scope | gate | status |
| --- | --- | --- | --- |
| A | Backend read API (list + summary with unified-activity merge) | unit + integration tests | [x] landed 2026-04-21 |
| B | Frontend list page (port, adapter, hooks, `LeadsPage`, nav entry) | `npm run lint && npm run build && npm run test` | [x] landed 2026-04-21 |
| C | Frontend detail page (route, hero, unified timeline, rail, raw snapshot) | `npm run lint && npm run build && npm run test` | [ ] pending |
| D | Cross-linking polish (sourceLeadId → lead detail from workflow runs / processed calls / webhooks) | `npm run lint && npm run build && npm run test` | [ ] pending |

Feature-end: `./mvnw test` (per user directive 2026-04-21 — backend suite
runs once at the end, not per slice).

## UX decisions at time of implementation
- **Detail surface = route** (D-Lead.1), not modal. Overrides `plan.md`'s
  modal-first framing. See `plan.md` supersession banner + `phases.md` for
  full decision record.
- **Activity = unified timeline** (D-Lead.2), not three separate "recent"
  sections.
- **No refresh endpoint** — live refresh is a query param on
  `GET /admin/leads/:id/summary?includeLive=true`, re-fired via
  `queryClient.invalidateQueries` with cache bust.

## Branch note
Implementation rides on `feature/workflow-builder-storyboard` per user
directive 2026-04-21. AGENTS.md §Branch strategy would normally require a
dedicated `feature/leads-admin-ui` parent branch off `dev`; this deviation
is explicit and acknowledged.

## Implementation log
_Entries are appended as each slice lands._

### Slice A — Backend read API (2026-04-21)

**Shipped**
- `GET /admin/leads` — cursor-paginated list (optional filters: `sourceSystem`, `status`, `sourceLeadIdPrefix`, `from`, `to`, `limit`, `cursor`). Ordering: `updated_at DESC, id DESC`. Cursor is base64 JSON `{updatedAt, id}` via `LeadFeedCursorCodec`.
- `GET /admin/leads/{sourceLeadId}/summary?sourceSystem=FUB&includeLive=false` — single-call aggregation: local lead row + optional live FUB refresh (via `LeadUpsertService.upsertFubPerson`) + top-10 per-stream recent arrays (calls / workflow runs / webhook events) + unified top-20 activity timeline.
- Live-fail contract: when `includeLive=true` and the FUB call throws, response is still 200 with `liveStatus=LIVE_FAILED`, `liveMessage=<reason>`, and local snapshot intact. Only `FUB` source with a numeric `sourceLeadId` attempts a live fetch; other combos return `LIVE_SKIPPED`.
- 404 when lead not found; 400 (`InvalidLeadFeedQueryException`) when `from > to`.

**Files**
- Controller: `controller/AdminLeadController.java`
- Service: `service/lead/LeadAdminQueryService.java`, `service/lead/LeadFeedCursorCodec.java`
- Repos: `persistence/repository/LeadFeedReadRepository.java`, `JdbcLeadFeedReadRepository.java`; derived `findTop10BySourceLeadId…` methods added to `ProcessedCallRepository`, `WorkflowRunRepository`, `WebhookEventRepository`
- DTOs: `LeadFeedItemResponse`, `LeadFeedPageResponse`, `LeadSummaryResponse`, `LeadActivityEventResponse`, `LeadActivityKind`, `LeadLiveStatus`, `LeadRecentCallResponse`, `LeadRecentWorkflowRunResponse`, `LeadRecentWebhookEventResponse`
- Exception: `exception/lead/InvalidLeadFeedQueryException.java`

**Tests** (15 pass, 0 fail)
- `JdbcLeadFeedReadRepositoryTest` — ordering, filters (source / status / prefix / window), cursor tiebreak on `(updated_at, id)`, partial-cursor rejection, limit.
- `LeadScopedTopNRepositoryTest` — the three new `findTop10…` methods: lead scoping, newest-first ordering, cap at 10.
- `AdminLeadsFlowTest` — list ordering, cursor continuity, `from>to` → 400, summary local-only with `LIVE_SKIPPED`, summary `includeLive=true` with stubbed `FollowUpBossClient` failure → `LIVE_FAILED` + 200 + local data, 404 for missing lead.

**Deviations from plan**
- Dropped `POST /admin/leads/{id}/refresh` — see phases.md §C / superseded banner on plan.md. Refresh is reached by re-requesting the summary endpoint with `includeLive=true`.

### Slice B — Frontend list page (2026-04-21)

**Shipped**
- New admin route `/admin-ui/leads` (rail entry `LD`, nav label "Leads"). `LeadsPage` reuses `FilterBar` + `DataTable` + `PageCard` primitives. Filters: `sourceSystem` input, `status` select (ALL / ACTIVE / ARCHIVED / MERGED), `sourceLeadIdPrefix` input, `from` / `to` date inputs. Apply/Reset + cursor-based Next pagination. Row click navigates to `routes.leadDetail(sourceLeadId)` (target route lands in Slice C).
- Ports/adapters: `LeadsPort` interface + `HttpLeadsAdapter` (Zod-validated) wired through `platform/container.ts`. Detail path uses `encodeURIComponent` on `sourceLeadId`.
- Search params round-trip via `parseLeadsSearchParams` / `createSearchParamsFromState` so filters + cursor are shareable URLs. Draft filter state mirrors the `WebhooksPage` pattern (keyed by committed-state fingerprint so external URL changes reset the draft).

**Files**
- Types: `ui/src/shared/types/lead.ts`
- Port/adapter: `ui/src/platform/ports/leadsPort.ts`, `ui/src/platform/adapters/http/httpLeadsAdapter.ts`, `ui/src/platform/adapters/http/leadSchemas.ts`
- Wiring: `ui/src/platform/container.ts`, `ui/src/platform/query/queryKeys.ts`, `ui/src/app/router.tsx`
- Labels/nav: `ui/src/shared/constants/routes.ts` (added `leads` nav item + `routes.leads` / `routes.leadDetail`), `ui/src/shared/constants/uiText.ts` (added `leads` block — list labels now, detail labels staged for Slice C)
- Module: `ui/src/modules/leads/ui/LeadsPage.tsx`, `ui/src/modules/leads/data/useLeadsQuery.ts`, `ui/src/modules/leads/lib/leadSearchParams.ts`, `ui/src/modules/leads/lib/leadDisplay.ts`

**Tests** (UI gate: 65 files / 314 tests pass, lint + build clean)
- `ui/src/test/http-leads-adapter.test.ts` — list query-param serialization + feed-page parsing; summary `sourceLeadId` URL-encoding + `includeLive` forwarding.

