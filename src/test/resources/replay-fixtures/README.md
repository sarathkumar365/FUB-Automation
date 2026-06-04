# Replay fixtures

Recorded webhook sequences driven through the engine by
[`ReplayHarnessTest`](../../java/com/fuba/automation_engine/replay/ReplayHarnessTest.java).

Each fixture file becomes one dynamic test. Drop a new JSON file in this
directory and it is picked up automatically — no Java changes needed.

## File format

One JSON object per file. Top-level fields:

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | yes | Identifier; appears as the dynamic-test name. Use kebab-case + a short scenario description. |
| `description` | string | yes | Free-form prose explaining what the scenario represents and why it is interesting. |
| `personSnapshots` | object | no | Map of `personId → JSON` the mock FUB client returns from `getPersonRawById`, applied BEFORE the first webhook (state at t=0). Keys are strings; values are the full FUB person payload. |
| `workflows` | array | no | Workflows to seed for THIS fixture. Each: `{ key, trigger, graph? }`. If omitted, the harness seeds its default `{ "on": "person.created" }` trivial workflow. Lets a fixture drive any trigger (e.g. `person.state_changed` + a `change.*` filter) and graph (e.g. a `delay` entry so the run stays `PENDING` and collisions are observable). |
| `events` | array | yes | The webhook sequence. Each event: `deltaMs` (offset from t=0), `eventId`, `event` (FUB type, e.g. `peopleUpdated`), `resourceIds` (array of long), and optional `personSnapshots` (see below). |
| `drainTimeoutMs` | integer | no | Max wait for expectations after the last webhook. Default 10000. |
| `expected` | object | no | Assertions — a mix of min-thresholds and exact counts (see table). |

**Per-event snapshots** (`events[].personSnapshots`): a map `personId → JSON` applied to the mock client *right before that webhook is posted*. This models FUB's ID-only webhooks where the follow-up `GET /people/{id}` returns **state-at-read-time** — so a fixture can make a person's `assignedUserId` (or any field) **change between events**, which is what enables reassignment / stage-change / collision scenarios. Latest write wins.

`expected` fields:

| Field | Type | Notes |
|---|---|---|
| `minWebhookEvents` | int | Lower bound on `webhook_events` rows |
| `minWorkflowRunsForPerson` | map `personId → int` | Lower bound on `workflow_runs` per person |
| `expectedTotalRunsForPerson` | map `personId → int` | **Exact** total runs (any status) per person |
| `expectedSupersededRunsForPerson` | map `personId → int` | **Exact** count of runs ending `CANCELED` with `reason_code = SUPERSEDED_BY_NEWER_EVENT` (Phase 5) |
| `expectedCreatedEventsForPerson` | map `personId → int` | **Exact** `person.created` domain events per person |
| `expectedStateChangeEventsForPerson` | map `personId → int` | **Exact** `person.state_changed` domain events per person (overshoot = collapse broken; undershoot = a real change went unreported) |
| `minReassignCalls` / `minCreateNoteCalls` | int | Lower bound on mock `FollowUpBossClient` write calls |
| `minAppendEvents` | map `eventKind → int` | Lower bound on append events (e.g. `note.created`) by kind |
| `notes` | string | Free-form; document expected pre/post behaviour |

> **Isolation:** each fixture fully resets DB + mock state at the start of its run (see `ReplayHarnessTest.resetState`), because `@TestFactory` runs `@BeforeEach` only once for the whole factory. Fixtures are independent; pick distinct person ids anyway for clarity.

## Naming conventions

- **Recorded incidents** (extracted from dev DB) → `lead-<id>-<short-name>-<yyyy-mm-dd>.json`
  Examples: `lead-20235-fub-burst-2026-05-12.json`, `lead-20123-echo-cascade-2026-05-08.json`
- **Synthesized scenarios** → `synthesized-<short-name>.json`
  These are for harness validation and edge-case probing, not historical incidents.

## Today's fixtures

**Recorded incidents (real DB extracts):**

| File | Scenario |
|---|---|
| `person-20235-fub-burst-2026-05-12.json` | Worst-case burst: 4 webhooks in 8s → collapses to 1 run |
| `person-20231-fub-burst-2026-05-12.json` | Single create; engine-uncaused |
| `person-20123-echo-cascade-2026-05-08.json` | Reassign echo cascade |
| `person-20207-triple-run-2026-05-11.json` | Triple-run + reassign echo |
| `synthesized-fub-burst-3-webhooks.json` | Synthesized 3-webhook burst (harness fidelity) |
| `synthesized-note-created.json` | `notesCreated` → one `note.created` append event |

**Behavioural scenarios (fresh-eyes, research-driven):**

| File | What it proves |
|---|---|
| `supersede-reassignment-collision.json` | Phase 5 end-to-end: assign A → reassign B supersedes the in-flight run; newest enforced |
| `supersede-chain-abc.json` | A→B→C: each newer assignment supersedes the prior; only C survives |
| `field-aware-no-supersede.json` | Assignment-run NOT superseded by an unrelated phone change (change-set overlap gate) |
| `multi-resource-burst-split.json` | One webhook with `resourceIds=[a,b]` fans out per person (FUB burst-splitting) |
| `out-of-order-update-before-create.json` | `peopleUpdated` before `peopleCreated` → INSERT recovery, no double-process (no ordering guarantee) |
| `duplicate-delivery-idempotency.json` | Same `eventId` twice → one run (at-least-once delivery) |

## Adding a real recorded fixture

1. Find the time window of interest in `webhook_events` for the relevant lead.
2. Export the rows (id, event_id, event_type, body, resource_ids, received_at) as JSON.
3. Convert each row into a `ReplayEvent` (deltaMs = `received_at - firstReceivedAt`, eventId, event, resourceIds).
4. Snapshot the FUB person state from `leads.person_details` at (or just before) the start of the window; put it in `personSnapshots`.
5. Add expected outcomes that document what the engine actually did in the incident (this is the "fidelity" check — the harness should reproduce the documented bad behavior).
6. Update `expected.notes` to describe what the scenario should look like AFTER each domain-events phase lands, so the same fixture can be used to verify each phase's win.

## Running

```
./mvnw test -Dtest=ReplayHarnessTest
```

Or run individual fixtures via your IDE's dynamic-test runner (each fixture
appears as a separately-runnable test).
