# Leads Page (List + Modal Detail Timeline) - Implementation Plan

## Summary
Build a new `/admin-ui/leads` operator page with:
- Lead list (cursor-paginated, ID-first search, exact filters).
- Lead detail modal (hybrid data: local snapshot first + best-effort live FUB refresh).
- Timeline usefulness in modal: recent processed calls, workflow runs, and lead-related webhook history.

This gives fast triage for "what happened to this lead?" without leaving the page.

## Key Changes (Backend + API + UI)

1. New backend read APIs
- `GET /admin/leads`
  - Query: `source` (default `FUB`), `status`, `from`, `to`, `limit`, `cursor`, `leadId` (sourceLeadId exact/starts-with; ID-first v1).
  - Response:
    - `items[]` (lead list rows from `leads` + selected `lead_details` projection)
    - `nextCursor`, `serverTime`
  - Sorting: `updated_at DESC, id DESC` keyset pagination.

- `GET /admin/leads/{sourceLeadId}/summary`
  - Query: `source` (default `FUB`), `includeLive` (default `true`)
  - Response blocks:
    - `lead` (local canonical row + projected snapshot fields)
    - `livePerson` (fresh FUB person when available)
    - `liveStatus` (`LIVE_OK | LIVE_FAILED | LIVE_SKIPPED`)
    - `recentProcessedCalls[]` (top 10 by `call_started_at DESC, id DESC`)
    - `recentWorkflowRuns[]` (top 10 by `created_at DESC, id DESC`)
    - `recentWebhookEvents[]` (top 10 by `received_at DESC, id DESC`, filtered by `source_lead_id`)
    - `stats` (counts + latest timestamps per section)
  - Failure behavior: if live FUB fails, return HTTP 200 with local data and `liveStatus=LIVE_FAILED` + non-fatal message.

2. Backend implementation shape
- Add `LeadAdminController` under `/admin/leads`.
- Add `LeadAdminQueryService` for orchestration only.
- Add read repositories:
  - `LeadFeedReadRepository` (JDBC keyset list).
  - Extend repositories for lead-scoped queries on `workflow_runs` and `webhook_events` (top-N by `source_lead_id`).
- Keep module boundaries:
  - controller -> service -> repository/port.
  - Live FUB call only via `FollowUpBossClient`.
- Reuse existing cursor codec pattern from webhooks (new codec or shared helper with different key names).

3. New UI module
- Add route: `/admin-ui/leads`.
- Add nav entry in rail/panel (`Leads`).
- Create `ui/src/modules/leads`:
  - `ui/LeadsPage.tsx`: table + filters + modal.
  - `data/useLeadsQuery.ts`, `data/useLeadSummaryQuery.ts`.
  - `lib/leadsSearchParams.ts` and display mappers.
- Platform additions:
  - New `LeadsPort` + HTTP adapter + Zod schemas.
  - Query keys: `leads.list(filters)`, `leads.summary(sourceLeadId, source, includeLive)`.
- UX details:
  - URL-backed filters for list state.
  - Row click opens modal (not route change).
  - Modal sections: Overview, Recent Calls, Workflow Runs, Webhooks.
  - Show "Live refresh failed; showing local snapshot" banner when applicable.

4. Data availability mapping (used by page)
- Local lead core: `leads` table (`source_system`, `source_lead_id`, `status`, `lead_details`, sync timestamps).
- Call timeline: `processed_calls` (`source_lead_id`, outcome, direction, duration, started time, task/rule/failure).
- Workflow timeline: `workflow_runs.source_lead_id`.
- Webhook timeline: `webhook_events.source_lead_id` (primarily assignment-domain lead events).

## Test Plan

1. Backend
- Controller tests:
  - `GET /admin/leads` happy path + invalid `from/to`.
  - `GET /admin/leads/{id}/summary` with and without live fetch.
- Repository/read-model tests:
  - keyset cursor correctness and stable ordering for leads list.
  - top-N retrieval for calls/runs/webhooks scoped by `source_lead_id`.
- Service tests:
  - graceful fallback when `FollowUpBossClient.getPersonRawById` fails transient/permanent.
  - summary payload completeness with empty related sections.
- Integration tests:
  - seed lead + related rows, assert summary aggregation contract.

2. Frontend
- Adapter schema tests for leads list and summary.
- Hook tests for query key/useQuery behavior.
- Page tests:
  - filter apply/reset and URL sync.
  - cursor "next" flow.
  - row selection opens modal and renders sections.
  - live failure banner behavior with local fallback data.
- Routing/nav tests to confirm `/admin-ui/leads` availability.

## Assumptions and Defaults
- Default source is `FUB`.
- V1 search is ID-first (`sourceLeadId`) with exact filters; no universal text search yet.
- Pagination is cursor next-only.
- Detail is modal (same page context), not a dedicated route.
- Summary endpoint is aggregation-first (single call for modal usefulness).
- Repo process reminder for implementation phase:
  - keep repo-wide decision alignment in `Docs/repo-decisions/`
  - track feature work in `Docs/features/<feature-slug>/` (`research.md`, `plan.md`, `phases.md`, and phase implementation log).
