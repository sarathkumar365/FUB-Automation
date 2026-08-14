# Replay harness

Test infrastructure for driving recorded webhook sequences through the live engine and asserting on downstream effects. Phase 0 of the [domain-events feature](../../../../../../../Docs/features/domain-events/plan.md).

If you are looking for the **fixture format** specifically, see [`src/test/resources/replay-fixtures/README.md`](../../../../../resources/replay-fixtures/README.md).

This README is about the **harness itself** — what it does, how it works, how to use it, and how to extend it.

---

## Why this exists

Every interesting bug in `Docs/features/agent-followup-enforcement/field-observations.md` is about **how multiple webhook events interact in time**. Unit tests miss this class of bug by construction; they can't reproduce "three peopleUpdated webhooks in 8 seconds." The replay harness is the project's first piece of infrastructure that can.

Two things the harness has to do well:

1. **Reproduce historical incidents deterministically** — feed in a real recorded sequence, see the same bad behavior as the field obs document.
2. **Validate that each domain-events phase actually fixes those bugs** — after Phase 2 lands, the same FUB-burst fixture should produce one workflow run instead of three. The fixture stays; the expectations evolve.

The harness is therefore both a **regression suite for current bugs** AND the **acceptance test for each phase's win**.

---

## How it works

```
┌────────────────────────────────────────────────────────────┐
│ ReplayHarnessTest (@SpringBootTest)                        │
│                                                            │
│  @BeforeEach: clear repos, seed test workflow              │
│                                                            │
│  @TestFactory replayAllFixtures():                         │
│    ReplayFixtureLoader.loadAll()                           │
│      → reads classpath:replay-fixtures/*.json              │
│      → deserializes to ReplayFixture records               │
│    For each fixture:                                       │
│      DynamicTest.dynamicTest(fixture.name, () -> runFixture)│
└────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────────┐
│ runFixture(fixture):                                       │
│  1. Script per-person FUB snapshots on the mock client     │
│  2. For each event:                                        │
│       sleep until scenarioStart + event.deltaMs            │
│       POST /webhooks/fub (HMAC-signed via test key)        │
│  3. Poll until expectations met OR drainTimeoutMs elapsed  │
│  4. Assert each expected.* threshold                       │
└────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────────┐
│ Real engine path (no test seams between MockMvc and DB):   │
│   WebhookIngressController                                 │
│   → WebhookIngressService (persist webhook_events)         │
│   → AsyncWebhookDispatcher (worker thread)                 │
│   → WebhookEventProcessorService (upsert person, dispatch)   │
│   → WorkflowTriggerRouter (match + plan)                   │
│   → WorkflowExecutionManager.plan (workflow_runs row)      │
└────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌────────────────────────────────────────────────────────────┐
│ Assertion surface (what fixtures check):                   │
│  - webhook_events row count                                │
│  - workflow_runs per source_person_id                        │
│  - ReplayHarnessFollowUpBossClient.reassignCalls()         │
│  - ReplayHarnessFollowUpBossClient.createNoteCalls()       │
└────────────────────────────────────────────────────────────┘
```

### Components

| Class | Responsibility |
|---|---|
| `ReplayHarnessTest` | The `@SpringBootTest` entry point. Wires Spring, seeds the test workflow, runs fixtures as dynamic tests, performs assertions. |
| `ReplayFixture` | Immutable record types describing one scenario (events, person snapshots, expectations). Pure data. |
| `ReplayFixtureLoader` | Scans `classpath:replay-fixtures/*.json` and deserializes via Jackson. Skips `README*` files. |
| `ReplayHarnessFollowUpBossClient` | Test double for `FollowUpBossClient`. Scriptable reads (`getPersonRawById`, `listPersonCalls`, `getCallById`), recorded writes (`reassignPerson`, `movePersonToPond`, `createNote`, `addTag`, `createTask`). Registered as `@Primary` via `TestConfig`. |

### Spring topology

- `@SpringBootTest` boots the full app context with the test profile (`src/test/resources/application.properties` — H2 in-memory, signing key `test-signing-key`, policy worker disabled).
- `@AutoConfigureMockMvc` provides `MockMvc` for posting webhooks without a real servlet container.
- `@TestConfiguration` inside `ReplayHarnessTest` declares a single `@Bean @Primary` for `ReplayHarnessFollowUpBossClient`. Spring registers it under both the concrete class AND the `FollowUpBossClient` interface; `@Primary` resolves against the real `FubFollowUpBossClient`.
- Async webhook dispatch uses real worker threads — the harness does not stub the worker pool. The polling-with-timeout pattern in `runFixture` is what handles the resulting non-determinism in timing.

### Why min-thresholds, not exact counts

`expected.minWorkflowRunsForPerson`, `expected.minReassignCalls`, etc. are lower bounds. Async timing races between webhook dispatcher, workflow trigger router, and the polling loop make exact-count assertions brittle. The bugs we care about are characterized as "≥ N parallel runs" or "≥ N reassigns" — minimums capture the behavior accurately AND are stable.

If a phase's win is "this number drops to exactly 1," express it as a *separate* upper-bound check (e.g. add a `maxWorkflowRunsForLead` field) rather than tightening `min` to equality.

---

## Running

```bash
# Run all fixtures
./mvnw test -Dtest=ReplayHarnessTest

# Run a single fixture (matches by dynamic test name)
./mvnw test -Dtest=ReplayHarnessTest -Dgroups='!slow' \
  -DfailIfNoTests=false \
  -Dorg.junit.tests.includeNames='synthesized-fub-burst-3-webhooks'
```

Or run from IntelliJ — each fixture appears as a separately-runnable dynamic test under the `ReplayHarnessTest` class.

### Wall-clock time

The harness uses real `Thread.sleep` between events (`deltaMs` is honored as wall time). The synthesized 8-second fixture runs ~12 seconds including Spring boot. A fixture with a 30-min internal gap would run for 30 minutes — not viable. Test-clock injection is a Phase 0 follow-up (see below).

---

## Adding fixtures

See [`src/test/resources/replay-fixtures/README.md`](../../../../../resources/replay-fixtures/README.md) for the JSON format and naming conventions. Three-line summary:

- Drop a new JSON file → it becomes a new dynamic test on the next run. No Java changes.
- Fixture names should match the file basename (the loader sets `fixture.name` from the JSON `name` field, conventionally identical to the filename without `.json`).
- For real-incident fixtures, use the [`scripts/build-replay-fixture.sh`](../../../../../../../scripts/build-replay-fixture.sh) generator — it reads the local dev DB and emits a properly-shaped fixture JSON for a given `source_person_id`.

---

## Extending the harness

The shape is intentionally minimal; the most common extensions:

### Adding a new expected.* assertion

1. Add a field to `ReplayFixture.Expected` (record type — add a new component).
2. Update `expectationsMet()` and `assertExpectations()` in `ReplayHarnessTest` — add one branch each. Follow the existing pattern (compare against threshold; assert with a `fixture=name` prefix).
3. Backfill existing fixtures if needed.
4. Document the new field in the fixture README.

### Adding a new FUB mock method or behavior

The `ReplayHarnessFollowUpBossClient` implements the full `FollowUpBossClient` interface. If FUB adds a new method or a test needs new recorded data:

- Reads → add a scriptable map keyed by entity id, plus a setter (`setPersonCalls`, `setCallDetails` are the patterns).
- Writes → add a `CopyOnWriteArrayList` for recorded invocations, plus an accessor. Use a record for the invocation shape (`ReassignInvocation`, `MoveToPondInvocation`).
- Reset state in `reset()` so fixtures don't bleed.

The existing `TestFollowUpBossClient` inside `WebhookProcessingFlowTest` is intentionally NOT shared — that class is focused on call-processing scenarios. Keeping them independent avoids tangled coupling. If a third test class needs FUB mocking and reuses substantial behavior, then promote a common base class.

### Adding support for a new webhook event type

The harness today posts to `/webhooks/fub` with `event = "peopleUpdated"`. To replay `callsCreated`, `noteCreated`, etc.:

- The fixture JSON event already supports `event` as a string — pass `"callsCreated"`.
- `resourceIds` becomes the call/note id.
- Mock client returns from `getCallById(id)` or whatever the new path consumes.
- Add a workflow trigger that matches the new event type if you want to assert on workflow runs spawning.

### Asserting on engine internal state (post Phase 2+)

Once Phase 2 lands and `state_change_events` rows exist, you'll likely want `expected.minStateChangeEvents` and similar. Pattern:

1. Inject the new repository into `ReplayHarnessTest`.
2. Add the threshold field to `ReplayFixture.Expected`.
3. Wire it through `expectationsMet` + `assertExpectations`.

For more complex assertions (e.g. "the diff payload contains `assignedUserId` in `changed_fields`"), prefer a custom matcher rather than expanding the `Expected` record indefinitely. The record should stay scenario-level; rich payload inspection lives in fixture-specific test methods.

---

## Known limitations + future improvements

| Limitation | Workaround today | Future fix |
|---|---|---|
| **H2 instead of Postgres** | Default test profile uses H2. Most engine behavior is identical. | Add a `ReplayHarnessPostgresTest` variant using `@Testcontainers` when Phase 5's partial unique index lands (H2 doesn't support partial uniques). |
| **Real wall-clock sleeps** | Fine for fixtures with internal gaps <30 seconds. | Inject a test `Clock` AND make the workflow worker manually drivable. WorkflowEngineSmokeTest already has the worker-trigger pattern — copy it. |
| **`@Primary` collision risk** | Only one `@Primary FollowUpBossClient` allowed. If another test config tries to override, Spring will fail loudly. | If multiple test suites need different FUB mocks, switch to `@ActiveProfiles` or qualifier-based selection. |
| **Polling-based drain** | The harness polls every 50ms up to `drainTimeoutMs`. Slow tests on a loaded CI box may flake. | Subscribe to a `DomainEventDispatcher` test listener (Phase 2 will introduce one) — push notification of completion instead of polling. |
| **No recording mode** | Fixtures must be hand-authored from DB extracts (see the fixture README). | A `RecordingMode` that wraps `WebhookIngressService` and writes incoming webhooks to a JSON file as they arrive. Useful for capturing new incidents. |
| **No partial assertions on workflow steps** | We assert on `workflow_runs` count, not on step outcomes. Sufficient for current scenarios. | Add `expected.minStepsByType` or similar when a phase requires step-level validation. |
| **One global test workflow** | `seedTestWorkflow()` registers exactly one workflow at setup. | If a fixture needs multiple workflows on the same person (cross-workflow uniqueness tests for I3/I4), add a `workflows` array to the fixture JSON and seed per-fixture. |

---

## Relationship to other tests

| Test | Scope | When to use |
|---|---|---|
| `ReplayHarnessTest` (this) | Multi-event scenarios; real Spring stack; assert on aggregate effects | Validating phase wins; reproducing field-observed incidents |
| `WebhookProcessingFlowTest` | Single-event call-processing flows | Testing the existing call → task pipeline |
| `WorkflowEngineSmokeTest` | Workflow engine internals with manual planner calls + manual worker triggers | Testing graph topology, retry, step state |
| Step-level unit tests (e.g. `BranchOnFieldWorkflowStepTest`) | Single step in isolation | Testing one step type's logic |

Use the replay harness when the bug or feature is **about timing or sequencing**. Use one of the others when it's about a single decision in isolation.

---

## When this README goes stale

The harness is going to evolve through Phases 1–5. Update this README whenever you:

- Add a new `expected.*` assertion field
- Add a new component file under `replay/`
- Change the Spring topology (e.g. swap to Testcontainers)
- Add a new fixture category (recording mode, parameterized fixtures, etc.)

A stale README that says the harness does X when it does Y is worse than no README. If you can't update it in the same PR as the change, file a follow-up issue immediately.
