# Phase 3 — Race Matrix and Plan-Revision Findings

> **Status:** Research. Drives a forthcoming `phase-3-plan.md` and a revision to [`phases.md`](./phases.md) §"Phase 3". Not yet approved as a plan.
>
> **Purpose:** Stress-test the thesis that *"local-state-first writes + `findBy…ForUpdate` pessimistic lock = echo suppression"* against every meaningful combination of (entity, operation, timing, concurrency). Be brutally honest about where the thesis holds, where it breaks, and what infrastructure each failure mode actually needs.
>
> **Conclusion in one line:** The thesis holds for ~50% of real scenarios (single scalar engine write, no concurrent activity, well-behaved echo). The other ~50% need the tracker, accept silent data loss, or — for `fub_create_note` — have no mechanism in the current plan at all.

---

## Reading order

1. [Smoking gun](#smoking-gun) — one structural defect that, if unaddressed, will pin the row lock across every FUB HTTP call. This is the highest-priority finding.
2. [The four step types as they exist today](#the-four-step-types-as-they-exist-today) — none touch local state. Phase 3 adds net-new behaviour, not a modification.
3. [The matrix](#the-matrix) — A1–A7, B (= A), C1–C3, D1–D4. Brutal cell-by-cell evaluation.
4. [Scalability and dependability](#scalability-and-dependability) — lock contention, tracker memory, durability, pattern enforcement, hidden coupling.
5. [Proposed Phase 3 plan revisions](#proposed-phase-3-plan-revisions) — concrete changes to [`phases.md`](./phases.md) deliverables.
6. [A separate race harness](#a-separate-race-harness) — why the existing harnesses can't cover this and what the new one looks like.

---

## Smoking gun

[`WorkflowStepExecutionService.executeClaimedStep`](../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepExecutionService.java) is `@Transactional` at line 68. The step's `execute(context)` — which performs the FUB HTTP call inside each `Fub*WorkflowStep` — runs **inside that outer transaction** at line 121.

**Implication for Phase 3's wrap pattern:** if the wrap takes `findBy…ForUpdate` and updates local state from inside `execute()`, the pessimistic row lock is held across the FUB HTTP call (200–800 ms per call) **by default**, because the outer transaction stays open for the entire step. Every concurrent webhook for the same person blocks waiting on that lock for the full FUB round-trip. Under FUB-burst load this pins the 2–4 thread webhook async pool.

This inverts the win of Phase 2's lock discipline (which only held the lock for the duration of an in-memory diff + save — milliseconds).

**Fix is not optional and must be designed up front:** the wrap's local update and tracker record MUST run in a `REQUIRES_NEW` inner transaction (or fully outside the outer `@Transactional` on `executeClaimedStep`), commit, release the lock, *then* call FUB. On failure, a second `REQUIRES_NEW` tx performs the revert.

Anyone implementing Phase 3 without internalising this will produce a working unit test (mocked FUB call returns immediately) and broken production behaviour (real FUB call holds the lock).

---

## The four step types as they exist today

`grep -E "personRepository|PersonEntity|personUpsertService|setPersonDetails|getPersonDetails"` against each of `FubReassignWorkflowStep`, `FubMoveToPondWorkflowStep`, `FubCreateNoteWorkflowStep`, `FubAddTagWorkflowStep` returns **zero matches**. Today these steps call FUB and return. They never read or write local Person state.

This is structurally good news. There is no entrenched pattern to fight; Phase 3 designs the local-write shape from scratch. The bad news is that the four steps' surface area today is misleadingly simple — `FubReassignWorkflowStep` is 132 lines. The wrap pattern roughly doubles each step's body, and the multi-tx discipline is non-trivial.

**FUB call shapes per step (from [`FollowUpBossClient`](../../../src/main/java/com/fuba/automation_engine/service/FollowUpBossClient.java)):**

| Step | Method | FUB echoes |
|---|---|---|
| `fub_reassign` | `reassignPerson(personId, targetUserId)` — PUT person | `peopleUpdated` (`assignedUserId`, `assignedTo` changed) |
| `fub_move_to_pond` | `movePersonToPond(personId, targetPondId)` — PUT person | `peopleUpdated` (`assignedPondId` changed) |
| `fub_add_tag` | `addTag(personId, tagName)` — PUT person | `peopleUpdated` (`tags` accumulating change) |
| `fub_create_note` | `createNote(command)` — POST note | `notesCreated` (the new note) **plus probable** `peopleUpdated` (person's `lastNoteAt` or similar metadata) |

The first three echo as `peopleUpdated`. **`fub_create_note` echoes in a different domain entirely (`notesCreated`)**, and likely produces a second `peopleUpdated` echo for the person's note-metadata change. This is the key reason why [Section D](#d-fub_create_note--entity-creation--no-local-notes-table) of the matrix concludes the plan is structurally incomplete for note creation.

---

## The matrix

### Legend

- **E** = engine write (workflow step)
- **W** = webhook handler (`PersonUpsertService.upsertFubPerson`)
- **L** = pessimistic row lock on `persons` (`findBy…ForUpdate`)
- **t<sub>x</sub>** = milliseconds since E started
- **✓** = lock + local-state-first thesis holds without further help
- **✗** = thesis breaks; needs tracker, accepts data loss, or has no mechanism

### A. `fub_reassign` (scalar — `assignedUserId`)

| # | Scenario | What actually happens | Thesis |
|---|---|---|---|
| **A1** | Happy path. Single engine write; echo arrives later. | E commits Alice (t<sub>0</sub>). FUB PUT lands (t<sub>200</sub>). Echo arrives (t<sub>500</sub>). W takes L, reads local Alice, payload Alice, diff empty. No event. | ✓ |
| **A2** | Echo arrives during E's inner-tx commit. | W takes L, blocks until E commits and releases. Then reads new local, diff empty. No event. | ✓ (lock closes the race) |
| **A3** | FUB-burst echo (3–4 webhooks for one logical change). | All 3–4 serialize on L; each reads identical local; all diffs empty. No events. | ✓ |
| **A4** | **Real concurrent external change.** Another user reassigns the same person to Carol via FUB UI at t<sub>2</sub>. | E commits Alice (t<sub>0</sub>), releases L. External-W for Carol arrives (t<sub>30</sub>), takes L, reads local Alice, payload Carol → emits real event Alice→Carol, writes local Carol. E's FUB PUT for Alice lands at FUB (t<sub>200</sub>), **clobbering Carol at FUB**. Echo for Alice arrives (t<sub>500</sub>), takes L, reads local Carol, payload Alice → **emits phantom event Carol→Alice**. | ✗ — phantom event. Only the tracker (annotating the final echo as `source=ENGINE`) can suppress it. |
| **A5** | FUB write fails (5xx) → revert. No concurrent activity. | E captures Bob, writes Alice locally, calls FUB, FUB returns 500. E re-takes L, restores Bob, commits. Local now Bob. Net diff vs. pre-engine: none. No event. | ✓ |
| **A6** | **FUB write fails → revert, with concurrent legitimate change.** Carol-webhook arrives between local-commit and FUB-failure. | E commits Alice (t<sub>0</sub>). External-W for Carol (t<sub>20</sub>) → local Carol. FUB returns 500 (t<sub>50</sub>). E reverts to **Bob** (captured prior) → **destroys Carol locally**. Will self-heal *only when FUB sends another `peopleUpdated` for this person* — could be hours or days. | ✗ — silent data loss. Plan §5 chose "restore prior snapshot, not undo delta"; this is the cost. Acceptable for dev phase; needs a documented production decision before scaling. |
| **A7** | **Two engine writes back-to-back.** Workflow A writes Alice, Workflow B writes Carol, both claim work on the workflow pool within milliseconds. | A takes L, captures Bob, writes Alice, releases. B takes L, captures **Alice** (as prior), writes Carol, releases. A's FUB PUT and B's FUB PUT race. If **A's PUT lands second** (network reorder), FUB ends up Alice; echo Alice → local Carol → diff → **emits phantom Carol→Alice**. | ✗ under PUT reorder. The tracker (with entries for both writes) is the only mechanism — the final echo would match A's tracker record → annotate `source=ENGINE` → workflow filter suppresses. **Local-state-first does not help here; the tracker is the load-bearing piece.** |

**Verdict for `fub_reassign`:** 4 of 7 cells hold on local-state-first + lock alone. 3 cells (A4, A6, A7) need the tracker, accept silent data loss, or both.

### B. `fub_move_to_pond` (scalar — `assignedPondId`)

Structurally identical to `fub_reassign`. Same scalar field semantics, same FUB echo shape, same matrix outcomes. No separate walk needed. **If A1–A7 are addressed for `fub_reassign`, B is covered.**

### C. `fub_add_tag` (accumulating — `tags`)

| # | Scenario | What actually happens | Thesis |
|---|---|---|---|
| **C1** | Happy path. E adds `NEW` to `tags=[A,B]` → local `[A,B,NEW]` → FUB → echo `[A,B,NEW]`. | W reads local `[A,B,NEW]`, payload `[A,B,NEW]`, diff empty. No event. | ✓ |
| **C2** | **Concurrent external tag add lands BEFORE engine's FUB PUT.** External adds `X` at FUB (t<sub>5</sub>); FUB sends webhook with `tags=[A,B,X]` (no `NEW` — FUB hasn't processed our PUT yet). | E commits local `[A,B,NEW]` (t<sub>0</sub>). External-W arrives (t<sub>20</sub>), takes L, reads local `[A,B,NEW]`, payload `[A,B,X]` → diff says **"NEW removed, X added"** → **emits phantom event claiming NEW was removed externally.** `NEW` was never on FUB; the "removal" is a lie about FUB state, indistinguishable from a real removal. Local becomes `[A,B,X]`. E's PUT lands at FUB (t<sub>200</sub>) → FUB now `[A,B,X,NEW]`. Echo (t<sub>500</sub>) → local `[A,B,X]`, payload `[A,B,X,NEW]` → diff `NEW added` → **second phantom event "NEW added by engine"**. | ✗✗ — two phantom events, one of them a fabricated "removal." **This is a fundamental defect of local-state-first for accumulating fields**: we make local diverge from FUB in a way the diff machinery cannot distinguish from a real external removal. |
| **C3** | FUB add_tag fails → revert. Concurrent external tag `Y` was added between our commit and FUB's failure. | E captures `[A,B]`, writes `[A,B,NEW]` (t<sub>0</sub>). External-W for `Y` → local `[A,B,NEW,Y]`. FUB returns 500 (t<sub>50</sub>). E reverts to **`[A,B]`** (captured prior) → **destroys `Y` locally**. Emits phantom event "`Y` removed, `NEW` removed." | ✗ — destroys legitimate concurrent tag. Same trade-off as A6 but worse because accumulating fields aggregate multiple concurrent writers. |

**Verdict for `fub_add_tag`:** thesis holds for the happy path only. Any concurrent external `tags` write produces phantom "removal" events because local treats our optimistic state as ground truth. **Local-state-first is structurally wrong for accumulating fields.** Three viable options (deferred for decision — see [revisions](#proposed-phase-3-plan-revisions)):

- (a) Accept C2 and C3. Trust that workflows filter on `change.source` for engine writes; tolerate phantom external-removal events.
- (b) **Do not local-state-first for `tags`.** Call FUB, wait for echo, let `PersonUpsertService` apply truth-from-FUB. Annotate via tracker. Loses the "echo produces no event" win for tags but eliminates the phantom-removal class entirely.
- (c) Defer `fub_add_tag` wrapping to a later phase, after a real workflow exposes the trade-off.

### D. `fub_create_note` (entity creation — no local notes table)

| # | Scenario | What actually happens | Thesis |
|---|---|---|---|
| **D1** | Happy path. Engine POSTs to FUB → FUB returns note id 100 → FUB sends `notesCreated` webhook with id 100. | `WebhookEventProcessorService` dispatches → `NoteEmissionService.emit` → emits `note.created` event for id 100. **No local notes state exists** — local-state-first cannot apply. | ✗ — thesis fundamentally doesn't apply |
| **D2** | FUB also sends `peopleUpdated` for the note's side-effect on the person (`lastNoteAt`, last activity timestamp). | The person echo produces a real `person.state_changed` event because the engine never wrote `lastNoteAt` locally, so the diff is non-empty. | ✗ — extra phantom on the person side |
| **D3** | Engine creates note, FUB returns success, but no `notesCreated` webhook arrives (FUB delivery failure). | No event ever emitted. Downstream workflows waiting on `note.created` wait forever. | Out of Phase 3 scope (FUB delivery reliability), but worth recording. |
| **D4** | Engine creates 3 notes in quick succession (e.g., for 3 mentioned users). | 3 `note.created` events emit. No suppression. A workflow filtering on `note.created` fires 3 times for our own writes. | ✗ — tracker (annotation) is the only mechanism |

**Verdict for `fub_create_note`:** **the plan is structurally incomplete here.** `phases.md` lumps `fub_create_note` with the other three steps, but local-state-first doesn't apply (no local state), and FUB sends `notesCreated` (different domain) plus probable `peopleUpdated` (separate echo on the person). This needs:

- A tracker mechanism for `note.created` that keys on `(personId, noteContentHash?)` since the engine doesn't know FUB's note id until after the POST returns. Alternatively, the engine records on the tracker keyed by FUB's returned `noteId` immediately after the POST succeeds — but the tracker is only useful from then on; if the echo arrives before the POST returns (rare but possible), we miss the annotation window.
- A separate mechanism for the person-side echo (`lastNoteAt` change) — either tracker for that field too, or accept the phantom.

**This is the single most important plan-revision finding in the matrix.**

---

## Where the thesis holds vs. breaks — honest summary

| Cells where thesis holds | Why |
|---|---|
| A1, A2, A3, A5 | Single scalar engine write, no concurrent activity, well-behaved echo. Lock closes the during-commit race; local-state-first makes echo's diff empty. |
| B (all) | Same shape as A. |
| C1 | Happy path for accumulating field — works because no concurrent change. |

| Cells where thesis breaks | Mechanism needed |
|---|---|
| A4, A7 | Tracker annotation; workflows filter on `change.source` |
| A6 | Plan §5 accepts silent data loss; "self-heals on next webhook" is contingent on FUB sending future webhooks |
| C2 | **No clean fix.** Local genuinely thinks `tags=[A,B,NEW]` before FUB knows; any external webhook in between diffs against optimistic local and produces phantom removal |
| C3 | Plan §5 trade-off, worse for accumulating fields |
| D1, D2, D4 | Tracker-only; plan as written doesn't describe this |
| D3 | Out of scope (FUB delivery), but worth a known-issue entry |

**The thesis "lock + local-state-first does echo suppression on its own" is correct for ~50% of real scenarios across the 4 wrapped step types.** The other ~50% need the tracker, accept silent data loss, or have no mechanism in the current plan.

This is not an argument against Phase 3. It is an argument that the plan in `phases.md` understates the role of the tracker and overstates the role of local-state-first.

---

## Scalability and dependability

### Lock contention on the webhook async pool

- [`WebhookAsyncConfig`](../../../src/main/java/com/fuba/automation_engine/config/WebhookAsyncConfig.java) configures 2–4 threads.
- Each webhook handler takes `findBy…ForUpdate` for the duration of the diff + save (single-digit to low-tens of ms in Phase 2 measurements).
- **N=10 burst for one person → serialized at ~10–50 ms per lock-hold → 100–500 ms for the burst to drain.** Other persons unaffected (different rows, different locks). Pool not globally blocked.
- **OK at current volume. Concerning at 10x–100x.**
- The [smoking gun](#smoking-gun) makes this materially worse: until the wrap pattern uses `REQUIRES_NEW`, every Phase 3 engine write pins the lock for the full FUB round-trip (200–800 ms). A handful of concurrent engine writes for the same person serialize at ~half a second each.

### Tracker memory

- `ConcurrentHashMap<TrackerKey, EngineWriteRecord>` per [`plan.md` §6](./plan.md#L241).
- Entry size: small (key + changed-field set + runId + timestamp).
- 1000 writes/sec × 30 s TTL = 30 k entries. Trivial.
- **Not a real concern at any plausible scale.**

### Tracker durability

- Process crash → all tracker entries lost.
- During the 30-second window after crash, every echo produces a phantom event (no tracker record to match against).
- Plan punts to "Redis-backed tracker, future" ([`plan.md` §"Out of scope"](./plan.md#L308)).
- **For dev phase: fine.** **For production: this needs to land before production traffic, because crashes happen and the phantom-event class is exactly what Phase 3 exists to prevent.** Recommend promoting `RedisEngineWriteTracker` (or a persisted equivalent) from "future" to "Phase 4 prerequisite."

### Pattern enforcement

- Phase 3 wraps 4 step types. The pattern is **convention**, not type-enforced.
- Nothing prevents a 5th FUB-mutating step from being added without the wrap.
- **Recommendation:** introduce `interface FubMutatingStep extends WorkflowStepType` with required `capturePriorSnapshot` / `applyLocalUpdate` / `revertLocalUpdate` hooks. The orchestrator (a new `EngineWriteCoordinator`) drives the dance; the step provides only the field-level operations. Convention → SPI.
- Cost: one more file, one extra interface, ~50 LOC of orchestrator. Buys structural enforcement against the next forgotten wrap.

### Hidden coupling — optimistic local

- Phase 3 introduces an **informal invariant**: "local state for fields {`assignedUserId`, `assignedPondId`, `tags`} may be optimistically ahead of FUB by up to ~500 ms."
- Any reader of local Person state that assumes it reflects FUB ground truth is now wrong inside that window.
- [`PersonSnapshotResolver`](../../../src/main/java/com/fuba/automation_engine/service/person/PersonSnapshotResolver.java) is used in `RunContext.person` for workflow expressions. **Workflows can now act on optimistic state.**
- Whether that's a feature (workflows see immediate engine effects without waiting for echo) or a bug (workflows act on uncommitted FUB intent that may roll back) depends on the workflow.
- **Recommendation:** call this out explicitly in `phase-3-plan.md` so workflow authors understand the new semantics. Document an example: a workflow that runs immediately after `fub_reassign` and reads `person.assignedUserId` will see the new value even if FUB's write later fails and reverts.

---

## A separate race harness

### Why existing harnesses cannot cover this

- **[`ReplayHarnessTest`](../../../src/test/java/com/fuba/automation_engine/replay/ReplayHarnessTest.java):** drives recorded webhook sequences with payload-relative timing. **Cannot drive engine-write → external-webhook → echo races** because external-webhook arrival timing is not in any recording. Replay timing is end-of-the-pipeline; race scenarios require controlling timing relative to engine writes that didn't exist when the incident was recorded.
- **[`PersonUpsertConcurrencyStressTest`](../../../src/test/java/com/fuba/automation_engine/integration/PersonUpsertConcurrencyStressTest.java):** exercises only `PersonUpsertService.upsertFubPerson` under parallel calls. **No step execution, no FUB call timing, no tracker.** Right prior art for the lock-discipline pattern, wrong scope for Phase 3.

### What the new harness looks like

```text
class EngineWriteRaceHarness {
  // Fake FUB client with configurable per-method delay.
  //   followUpBossClient.reassignPerson(...) → wait Nms, return canned response (or 500)
  // CountDownLatch orchestration: release E and W threads at known offsets.
  //   schedule(E, +0ms), schedule(externalW, +20ms), schedule(echoW, +500ms)
  // Assertions:
  //   - events table contains exactly the expected (kind, entity, payload, source-annotation) rows
  //   - tracker has expected (hit, miss, eviction) counts
  //   - local Person state matches expected "FUB ground truth" at end of scenario
  // Scenarios = the matrix cells codified as tests (A1–A7, C1–C3, D1–D4).
}
```

### What it would catch beyond the matrix itself

- **The smoking gun structural defect.** A naive wrap that holds the lock across the FUB call would time out under fake-FUB-delay=500ms with concurrent webhooks, surfacing the lock-hold issue. Unit tests with mocked FUB return immediately and never expose this.
- **Tracker key collisions** under back-to-back same-person engine writes.
- **TTL-too-short regressions** if someone tunes the eviction interval.

### Sizing

- Skeleton + fake FUB client + CountDownLatch orchestration: ~1.5 days
- Scenarios for A1–A7: ~1 day
- Scenarios for C1–C3 (accumulating field): ~0.5 day
- Scenarios for D1–D4 (note creation): ~1 day, contingent on the note tracker design landing
- **Total: ~3–5 days**

Cheaper than debugging a phantom-event incident in production once Phase 4 ships and workflows consume domain events.

### Where it lives

`src/test/java/com/fuba/automation_engine/integration/EngineWriteRaceHarness.java` (sibling to `PersonUpsertConcurrencyStressTest`) plus fixture data in `src/test/resources/engine-write-race/`. Testcontainers Postgres for real lock semantics; fake FUB client wired via a `@TestConfiguration`.

---

## Proposed Phase 3 plan revisions

These are concrete amendments to [`phases.md`](./phases.md) §"Phase 3" deliverables. They will be applied in the next plan-lock pass once the user approves direction.

### Revision 1 — Reframe tracker role

Current `phases.md` reads as if local-state-first is the primary suppression mechanism and the tracker is auxiliary. **Reverse the framing:** local-state-first handles the common case (single scalar write, no concurrency); the tracker handles the wider failure surface (cells A4, A6, A7, C2, C3, D1, D2, D4 above).

The deliverable list stays the same; the **rationale and exit-criteria language** needs to acknowledge what each mechanism actually owns.

### Revision 2 — Carve out `fub_create_note` as a separate pattern

`phases.md` deliverable 2 ("Wrap engine-write step types — the **four** FUB-mutating steps") lists `fub_create_note` alongside the three person-mutating steps. **Split this:**

- **Sub-pattern A** (the three person-mutating steps): local-state-first + tracker annotation, as currently described.
- **Sub-pattern B** (`fub_create_note`): tracker-only. Engine records on the tracker immediately after the FUB POST returns (keyed by the returned `noteId`). When `notesCreated` echo arrives, dispatcher annotates `source=ENGINE` if the tracker has a recent record. Separately, the engine records on the tracker for the person's `lastNoteAt` (or whatever person-side fields FUB updates on note creation) so the `peopleUpdated` echo can also be annotated.

Document the residual race window (echo arrives before POST returns) and either accept it or design a synchronous record-before-POST that uses a content hash.

### Revision 3 — Decide explicitly on accumulating-field policy for `fub_add_tag`

The plan must pick one of:
- **(a) Local-state-first + accept C2/C3** — phantom "removal" events possible; rely on workflow `change.source` filtering.
- **(b) Tracker-only for tags** — do not write local optimistically; let FUB's echo be the source of truth; annotate via tracker. Loses the "echo produces no event" win but eliminates the phantom-removal class.
- **(c) Defer `fub_add_tag` wrapping** to a later phase.

**Recommendation:** (b). The phantom-removal class is genuinely dangerous because it's indistinguishable from a real external removal. The plan's lean of (a) carries this risk forward into Phase 4 workflows.

### Revision 4 — Make `REQUIRES_NEW` a pattern requirement, not a footnote

The [smoking gun](#smoking-gun) is the most common implementation defect Phase 3 can ship with. The plan must state explicitly:

> The wrap's inner write (`capture prior → update local → record tracker`) MUST run in a `REQUIRES_NEW` transaction (or fully outside the outer `@Transactional` on `executeClaimedStep`). The FUB HTTP call happens **after** the inner transaction commits. On FUB failure, the revert runs in a second `REQUIRES_NEW` transaction. Holding the row lock across the FUB call is incorrect and will pin the webhook async pool under burst load.

Mirrors the discipline already established in `PersonUpsertService` for `DIVE` recovery — same pattern, different motivation.

### Revision 5 — Promote tracker durability

`plan.md` Out-of-scope table lists "Redis-backed `EngineWriteTracker`" as "future." **Promote to a Phase 4 prerequisite, not Phase 3.** Document the in-memory tracker as dev-phase-only and the crash-window cost (~30s of phantom events) as the known trade-off.

The interface boundary is correct; only the impl swap needs to land before production traffic.

### Revision 6 — Pattern enforcement via SPI

Introduce `interface FubMutatingStep extends WorkflowStepType` with the capture / apply / revert hooks. The new `EngineWriteCoordinator` invokes these in the correct order with the correct transaction semantics. Steps that mutate FUB but do not implement this interface fail validation at registration time.

### Revision 7 — Document the optimistic-local invariant for workflow authors

Add a section to `phase-3-plan.md` (and reference from the workflow-authoring guide once it exists) stating that local Person state for the engine-wrapped fields can be optimistically ahead of FUB by up to ~500 ms. Give one example of a workflow that reads `person.assignedUserId` immediately after `fub_reassign` and sees the engine's intended value even if FUB's write later fails.

### Revision 8 — Add the race harness as a Phase 3 deliverable

Sized at ~3–5 days. Sibling to `PersonUpsertConcurrencyStressTest`. Scenarios A1–A7, C1–C3, D1–D4 codified as tests. Required for Phase 3 exit criteria.

---

## Risks revealed by the matrix (mid-flight detection signals)

| Risk | Signal during build |
|---|---|
| Wrap pattern holds lock across FUB call | Race harness with fake-FUB-delay=500ms + 10 concurrent webhooks → test exceeds threshold |
| Tracker TTL too short → phantom events on slow echoes | Tracker miss-rate metric > expected baseline (workflow-author-visible) |
| Accumulating-field optimism produces phantom "removal" events in dev | Replay harness extended with a synthesized concurrent external tag-add fixture |
| Note tracker keyed on `noteId` misses early echoes | Synthesized scenario in race harness with FUB POST-return delay > webhook arrival delay |
| `FubMutatingStep` SPI not adopted by new step types | Validator at workflow registration refuses to register an unwrapped FUB step |

---

## Open questions (need user input before drafting `phase-3-plan.md`)

1. **Accept silent data loss on A6/C3 revert, or rework revert semantics?** Plan §5 chose "restore prior snapshot." The matrix shows this destroys legitimate concurrent changes. Defaulting to "self-heals on next webhook" is dev-phase-acceptable; we should make the production decision explicit.
2. **Accumulating-field policy for `fub_add_tag`** — (a), (b), or (c)?
3. **Promote Redis-backed tracker to Phase 4 prerequisite?** Or accept the crash-window phantoms as a documented dev limitation indefinitely?
4. **`FubMutatingStep` SPI yes/no?** Convention is cheaper; SPI is enforceable. Where does the team want the discipline?
5. **Race harness as Phase 3 deliverable or follow-up?** The matrix says deliverable. The cost is ~3–5 days against the rest of Phase 3 (~5–8 days of substrate work). Roughly doubles the phase's size.

---

## Cross-references

- High-level Phase 3 deliverables: [`phases.md` §"Phase 3"](./phases.md#L305)
- Architectural rationale for local-state-first writes: [`plan.md` §5](./plan.md#L237)
- Tracker interface: [`plan.md` §6](./plan.md#L241)
- Out-of-scope (durable outbox, Redis tracker): [`plan.md` §"Out of scope"](./plan.md#L289)
- Phase 2 lock-discipline prior art: [`PersonUpsertService.upsertFubPerson`](../../../src/main/java/com/fuba/automation_engine/service/person/PersonUpsertService.java) + [`PersonUpsertConcurrencyStressTest`](../../../src/test/java/com/fuba/automation_engine/integration/PersonUpsertConcurrencyStressTest.java)
- Smoking gun: [`WorkflowStepExecutionService.executeClaimedStep`](../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepExecutionService.java) line 68 outer `@Transactional`
