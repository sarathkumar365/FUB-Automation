# Phase 0 — Replay harness — implementation log

Status: `IN PROGRESS` — harness framework + one synthesized fixture landed; four real fixtures (lead 20123, 20207, 20231, 20235) pending DB extraction.

## Goal

Build a test harness that drives recorded sequences of FUB webhooks through the live engine and asserts on downstream effects (workflow runs, FUB calls, persisted rows). Without this, Phases 2–5 are nearly impossible to validate — every bug in the field observations is about *interactions in time*, which unit tests miss by construction.

The harness must reproduce the documented bad behavior of the 05-08/05-11/05-12 incidents deterministically. The same fixtures will later validate that each phase's win actually lands (e.g. after Phase 2, the FUB-burst fixture should produce one workflow run instead of three).

## What landed in this commit

- `src/test/java/com/fuba/automation_engine/replay/`
  - `ReplayFixture.java` — record types for fixture shape
  - `ReplayFixtureLoader.java` — classpath JSON loader
  - `ReplayHarnessFollowUpBossClient.java` — test FUB client (scriptable reads, recorded writes)
  - `ReplayHarnessTest.java` — `@SpringBootTest` driving fixtures via `@TestFactory` dynamic tests
- `src/test/resources/replay-fixtures/`
  - `README.md` — fixture format spec + naming conventions
  - `synthesized-fub-burst-3-webhooks.json` — first fixture, harness fidelity validation

## Verification

- `./mvnw test -Dtest=ReplayHarnessTest` passes (11.87s wall clock — 3s context boot + 12s real-time replay of an 8-second event window + drain)
- Full test suite `./mvnw test` — 515 tests, 0 failures
- Synthesized fixture reproduces the documented bad behavior: three `peopleUpdated` webhooks for one lead within 8 seconds spawn three parallel workflow runs (`runId=1`, `runId=2`, `runId=3` visible in the trigger router logs). Same shape as 2026-05-12 lead 20235.

## Meaningful decisions

### H2 + MockMvc, not Testcontainers Postgres

Existing pattern in `WebhookProcessingFlowTest` already uses the default H2 + MockMvc setup. The harness inherits this for fast startup (~3s vs ~15s+ for Postgres container). Trade-off: H2 doesn't support partial unique indexes (Phase 5) or true JSONB operators. Plan: revisit when Phase 5 lands; some replay tests may need to move to Testcontainers if H2 limitations become blocking. Documented in the test class Javadoc.

### Fixture format — min-thresholds, not exact counts

Every `expected` field is a *minimum*, not an exact count. Async timing in Spring Boot tests makes exact assertions brittle (race between webhook dispatcher, workflow trigger router, and the polling assertion loop). The bad behaviors we care about are "≥ N parallel runs" or "≥ N reassigns" — minimum thresholds capture this fine and are stable.

### Real wall-clock for timing within fixtures

`deltaMs` is honoured via `Thread.sleep` between events. Tests using the synthesized 8-second burst take ~12 seconds total. Acceptable for Phase 0 — keeps the harness conceptually simple. If Phase 2/3/5 need fixtures with minute-long internal gaps (e.g. the full 30-min reassign timeline), we'll need test-clock injection at that point. Not solving it preemptively.

### Dynamic tests, not parameterized

`@TestFactory` produces one dynamic test per fixture file. Adding a new scenario = drop a JSON file; no Java changes. `@ParameterizedTest` was the alternative but would require an explicit provider method enumerating fixture names.

### Test workflow registered programmatically in setup, not via Flyway seed

Phase 6 of `agent-followup-enforcement` (the workflow JSON seed migration) is `NOT_STARTED`. The harness can't depend on a seed that doesn't exist. Registering programmatically also keeps the harness self-contained — fixtures don't accidentally affect other tests' workflow state via `@BeforeEach` cleanup.

### Single `@Primary` bean for the test FUB client

First attempt declared both a `@Bean @Primary ReplayHarnessFollowUpBossClient` AND a `@Bean @Primary FollowUpBossClient` delegating to it. Spring rejected this — "more than one 'primary' bean found." Fix: a single bean method returning the concrete class. Spring registers the bean under both the concrete type and the implemented interface; `@Primary` resolves against the real `FubFollowUpBossClient`.

## What this fixture demonstrates today

The `synthesized-fub-burst-3-webhooks.json` fixture reproduces the lead-20235 pattern at small scale: three `peopleUpdated` webhooks for one lead within ~8 seconds. Today's engine plans one workflow run per webhook → three parallel runs on the same lead, all proceeding independently. This is the behavior documented in `field-observations.md` for 2026-05-12.

Once Phase 2 lands (state diff + event emission, with the FUB-burst webhooks producing no diff after the first), the same fixture should produce **one** workflow run, not three. The fixture's `expected.notes` field documents this evolution so the same JSON file evolves with the phases.

## Repo decisions impact

`No` — local feature concern. The harness is internal test infrastructure; no cross-cutting decisions are introduced.

## Out of scope for Phase 0 (deferred)

- **Real DB-extracted fixtures for lead 20123, 20207, 20231, 20235** — pending payload export from dev DB. See [field-observations.md](../agent-followup-enforcement/field-observations.md) for the time windows.
- **Test-clock injection for minute-scale fast-forward** — needed if Phases 2/3/5 require fixtures with internal 30-minute gaps (full reassign timeline). Not preemptively built.
- **Testcontainers Postgres variant of the harness** — needed once Phase 5's partial unique index lands and H2 stops being adequate.
- **Recording mode** (capture a live webhook sequence into a fixture file) — would close the loop on harness usage but not needed for the current set of historical incidents.

## Next steps before Phase 0 is `DONE`

1. Extract real webhook payloads from dev DB for the four incidents listed above. Export shape: each webhook row's `event_id`, `event_type`, `body` (raw JSON), and `received_at` (used to compute `deltaMs`).
2. Snapshot the matching lead state from `leads.lead_details` at the start of each window for the `personSnapshots` field.
3. Convert each export into a `lead-<id>-<short-name>-<yyyy-mm-dd>.json` fixture under `replay-fixtures/`.
4. Run the harness — each fixture should reproduce the documented bad behavior.
5. Update `phases.md` status to `DONE`.
