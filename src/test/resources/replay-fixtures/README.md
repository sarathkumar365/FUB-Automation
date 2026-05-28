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
| `personSnapshots` | object | no | Map of `personId → JSON` that the mock FUB client returns from `getPersonRawById`. Set BEFORE the first webhook so `PersonUpsertService` sees real-looking data. Keys are strings (JSON object keys); values are the full FUB person payload. |
| `events` | array | yes | The webhook sequence to play. Each event has `deltaMs` (offset from t=0), `eventId`, `event` (FUB event type, e.g. `peopleUpdated`), and `resourceIds` (array of long ids). |
| `drainTimeoutMs` | integer | no | Max time to wait for expectations to materialise after the last webhook. Defaults to 10000. |
| `expected` | object | no | Assertions. All fields are min-thresholds, not exact matches — fixtures encode "at least this much happened" because async timing makes exact counts brittle. |

`expected` fields:

| Field | Type | Notes |
|---|---|---|
| `minWebhookEvents` | int | Lower bound on `webhook_events` row count |
| `minWorkflowRunsForPerson` | map of `sourcePersonId → int` | Lower bound on `workflow_runs` per lead |
| `minReassignCalls` | int | Lower bound on `FollowUpBossClient.reassignPerson` calls recorded by the mock |
| `minCreateNoteCalls` | int | Lower bound on `FollowUpBossClient.createNote` calls |
| `notes` | string | Free-form; useful for documenting what the scenario should look like AFTER each domain-events phase lands |

## Naming conventions

- **Recorded incidents** (extracted from dev DB) → `lead-<id>-<short-name>-<yyyy-mm-dd>.json`
  Examples: `lead-20235-fub-burst-2026-05-12.json`, `lead-20123-echo-cascade-2026-05-08.json`
- **Synthesized scenarios** → `synthesized-<short-name>.json`
  These are for harness validation and edge-case probing, not historical incidents.

## Today's fixtures

| File | Source | Scenario |
|---|---|---|
| `synthesized-fub-burst-3-webhooks.json` | Synthesized | Three `peopleUpdated` webhooks for one lead within ~8s; mirrors the lead-20235 pattern. Used to validate harness mechanics before real DB-extracted fixtures land. |

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
