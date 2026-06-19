# Reporting — data-model findings (ground truth)

> **Status: research / findings.** What data actually exists today, how it's written,
> and what each source can and can't answer accurately. Established by reading the code
> (two source digs, 2026-06-19). This is the factual basis the plan and the accuracy
> caveats rest on. Cite this rather than re-deriving.

## The three sources we have today

### 1. `events` — the domain-event diary (`EventEntity`)

Columns: `id`, `event_kind` (VARCHAR 64), `source_system`, `source_event_id`,
`entity_type`, `entity_id`, `payload` (JSONB), `created_at` (TIMESTAMPTZ).
Indexes: `(event_kind, created_at)`, `(entity_type, entity_id, created_at)`.

**The complete set of `event_kind` values emitted today (6):**

| event_kind | written by | meaning |
|---|---|---|
| `person.created` | `PersonUpsertService` | a person row was **first inserted** (see caveat) |
| `person.state_changed` | `PersonUpsertService` | a tracked person field changed (carries `changed_fields`, `previous`, `current`) |
| `call.created` | `CallUpsertService` | a call webhook was processed |
| `note.created` / `note.updated` / `note.deleted` | `NoteEmissionService` | note lifecycle |

There is **no** task event, appointment event, user/agent event, or SMS/text event.

### 2. `processed_calls` — call facts (`ProcessedCallEntity`)

Key columns: `call_id` (unique), `status` (enum: RECEIVED / PROCESSING / SKIPPED /
TASK_CREATED / FAILED), `source_person_id` (VARCHAR, **the lead**, nullable),
`source_user_id` (BIGINT, **the agent**, nullable), `is_incoming` (BOOLEAN — true=inbound),
`duration_seconds`, `outcome` (VARCHAR 64, **free-text from FUB**), `call_started_at`,
`raw_payload` (JSONB — full FUB response retained), `created_at`, `updated_at`.
Index: `(source_person_id, call_started_at)`.

- Source: FUB `callsCreated` webhook → `/v1/calls/{id}` fetched → mapped in
  `CallUpsertService.persistCallFacts()`.
- Both inbound and outbound captured (`is_incoming`).
- `outcome` is stored verbatim. Observed values: `"Connected"`, `"Voicemail"`,
  `"No Answer"`, `null`. **No enum, no DB constraint** — the value space is whatever FUB sends.

### 3. `persons` — the lead/agent mirror (`PersonEntity`)

Key columns: `source_system`, `source_person_id` (FUB personId as string), `status`
(ACTIVE / ARCHIVED / MERGED), `kind` (LEAD / AGENT / REALTOR / UNKNOWN), `person_details`
(JSONB snapshot), `previous_state` (JSONB), `created_at`, `updated_at`, `last_synced_at`.
Unique on `(source_system, source_person_id)`.

`person_details` snapshots these FUB fields:
`name, firstName, lastName, stage, stageId, type, source, assignedUserId, assignedTo,
assignedPondId, assignedLenderId, claimed, contacted, tags, phones, emails`.

- Agents are persisted too (`kind=AGENT`, mapped from `stage="Agent"`).
- **`assignedUserId` (the agent's FUB userId) and a person's `source_person_id` (a FUB
  personId) are different namespaces — they do NOT join.**
- **`assignedTo` holds the assigned agent's display name** (e.g. `"Sarath Kumar"`),
  captured alongside `assignedUserId`.

## FUB webhook coverage (what we receive vs. ignore)

Handled by `FubWebhookParser`: `callsCreated`, `peopleCreated`, `peopleUpdated`,
`notesCreated/Updated/Deleted`. **Everything else is logged and skipped** — including
`tasks*`, appointments, deals, and user/text-message events.

## Assignment: reconstructable, but not first-class

- There is **no assignment event**. Assignment is the `assignedUserId` field changing,
  surfaced inside `person.state_changed` (`changed_fields` contains `assignedUserId`,
  with `previous`/`current` values), **and** baked into `person.created` when a lead
  arrives already assigned.
- Full per-lead assignment history (incl. reassignment A→B) **is** reconstructable from
  the `events` diary — but you must **union both paths** (state_changed diffs + the
  create payload). Using the current `assignedUserId` snapshot alone gives only the
  latest owner, not the timeline.
- We do **not** record *who did the assigning* — only who it's assigned *to*. So
  "self-assigned vs. assigned-by-someone" is not distinguishable today.

## Agent-name resolution

- **The assigned agent's name is free** — `person_details.assignedTo`.
- There is **no global userId → name map.** `processed_calls.source_user_id` has no
  companion name, and FUB users are not ingested anywhere (no `/v1/users` client method,
  no users table). This is the codebase's **Issue #19** (low priority, deferred).
- Consequence for reporting: a board anchored on the **assigned** agent can label by name.
  Naming the maker of an *arbitrary* call (when it differs from the assignee) needs
  FUB-user ingestion — out of scope until then.

## Accuracy implications (the caveats every report inherits)

- **`person.created` = first-seen-by-us, not FUB intake.** A `peopleUpdated` for a
  never-seen person inserts it and stamps `created_at = now` — an old lead can look like
  it arrived today. FUB's own `created` date is not snapshotted (fixable: add it; FUB is
  system-of-record so it's backfillable).
- **`outcome` is free-text** — "contacted/connected" filters ride on observed strings,
  not an authoritative set; a FUB wording change breaks them silently.
- **Nullable attribution keys** (`source_user_id`, `source_person_id`) cap per-agent /
  per-lead accuracy. Because `raw_payload` is retained, a high null rate is diagnosable
  (FUB didn't send it = tolerate) vs. fixable (we didn't extract it = parse + backfill).
  **The `source_user_id` null rate is the ceiling on accountability accuracy** and is the
  one number to measure before trusting any per-agent figure.
- **Mirror completeness = webhook-delivery completeness.** We hold the leads FUB sent us
  webhooks for; a lead that never triggered one isn't present.

## What this makes answerable today (no new capture)

- **Assigned → called** (the MVP): assignment is on the lead (`assignedUserId`/`assignedTo`),
  calls are in `processed_calls` (join on `source_person_id`). Fully answerable from
  existing tables — **no mirror, no Vanna, no FUB-user ingestion.**
- **Blocked (needs the mirror / new capture):** task creation vs. completion (Q5/Q6),
  "useful" outcome (Q4, via appointments/deals), text/SMS contact, true FUB intake date.

See also: [v1-scope](../v1-scope.md), [architecture-direction](../architecture-direction.md).
