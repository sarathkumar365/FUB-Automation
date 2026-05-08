# Research

## Feature
`leads-admin-ui`

## Goal
Create a dedicated Leads page in the internal admin UI so operators can:
- browse all leads with useful operational filters
- inspect a lead quickly without leaving the list context
- understand lead activity across calls, workflow runs, and webhooks

## Relevant accepted decisions
- `Docs/repo-decisions/RD-001-normalized-lead-event-contract.md`
- `Docs/repo-decisions/RD-003-lead-identity-mapping-boundary.md`

## Current data inventory (confirmed)
- Canonical lead row: `leads` table (`source_system`, `source_lead_id`, `status`, `lead_details`, timestamps).
- Local call timeline: `processed_calls.source_lead_id` + call fact columns.
- Workflow timeline: `workflow_runs.source_lead_id`.
- Webhook timeline: `webhook_events.source_lead_id` (assignment-related lead events most complete).
- Live person fetch available through `FollowUpBossClient#getPersonRawById`.

## Existing UX patterns to align with
- Table-driven pages with filter bar + apply/reset.
- Cursor pagination pattern already used by webhooks.
- Shell-inspector rail + hero + accordion pattern adopted by the workflow-run
  detail redesign (see `ui/Docs/workflow-ux-audit.md`). Lead detail follows
  the same pattern for consistency.
- URL-serializable filter state is expected for operability.

## UX decisions (updated 2026-04-21)
- **Detail surface:** dedicated route `/admin-ui/leads/:sourceLeadId` (not
  modal). Supersedes the modal-by-product-choice note above. Rationale:
  bookmarkable, shareable, cross-linkable from workflow-run / processed-call
  / webhook-event detail surfaces. Tracked as D-Lead.1 in `phases.md`.
- **Activity display:** one unified chronological timeline with client-side
  filter chips (All / Calls / Workflows / Webhooks), not three parallel
  "recent" lists. Tracked as D-Lead.2 in `phases.md`.
