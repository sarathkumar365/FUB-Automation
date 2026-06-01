# Phase 3 — Implementation Log (wrap-up)

Status: `COMPLETE` — all five sub-phases shipped. 682 tests green.

Decision narrative per the AGENTS.md convention: *why was it built this way?* — git is the source of truth for what changed. 3a and 3b have their own detailed logs ([phase-3a-implementation.md](./phase-3a-implementation.md), [phase-3b-implementation.md](./phase-3b-implementation.md)); this file records 3c–3e and the phase-level outcome.

## What Phase 3 delivered

Every engine-originated FUB write now flows through `EngineWriteCoordinator`, which records the intent on `InMemoryEngineWriteTracker` so the resulting echo webhook is either **suppressed** (scalar modes) or **annotated** `source=ENGINE` (tracker-only modes). All of it is dormant behind `engine.write.emit-events=false` — Phase 4 flips the flag and wires the first consumer. The bad-run-rate win arrives in Phase 4; Phase 3 is pure substrate.

| Sub-phase | Step | Mode | Echo handling | Scenarios |
|---|---|---|---|---|
| 3b | `fub_reassign` | scalar (local-first) | echo diffs to empty → **0 events** | A1–A7 |
| 3c | `fub_move_to_pond` | scalar (local-first) | echo diffs to empty → **0 events** | A1/A3/A4/A5 (pond) |
| 3d | `fub_add_tag` | tracker-only append | echo emits **1 annotated** event | C1–C3 |
| 3e | `fub_create_note` | tracker-only entity-create | `note.created` echo emits **1 annotated** event | D1/D3/D4 |

The headline behavioural split a reviewer must hold: **scalar mode suppresses to zero; tracker-only modes emit one annotated event.** Different success criteria — scalar writes local first (so the echo is a no-op diff); tracker-only modes never write local optimistically (so the echo legitimately diffs and is merely stamped).

## 3c — `fub_move_to_pond` (no surprises)

A verbatim mirror of 3b for `assignedPondId`. No new pattern; no separate decision log. The only carry-over gotchas (both from 3b): the coordinator's `findBy…ForUpdate` requires a pre-existing local person row, and test echo payloads must be built by JSON parsing (not `.put(long)`) so numeric node types match `PersonDiffComputer`'s type-strict equality.

## 3d — `fub_add_tag` (first tracker-only path)

`tags` is an accumulating field. Optimistic local-state-first writes are structurally unfixable here (race-matrix C2: a concurrent external tag change landing before our FUB PUT fabricates a phantom "tag removed" event). So 3d inverts the ordering: **FUB first, record on the tracker only on success, never write local.** Local catches up when FUB's echo arrives. C2 proves the payoff — an external concurrent add stays a real unannotated event and there is no phantom removal. Cost: one annotated event per engine tag-add (vs. zero in the rejected optimistic model), accepted in exchange for eliminating the phantom class.

## 3e — `fub_create_note` (single channel — the plan changed mid-flight)

3e was planned as **two channels**: the `note.created` echo *and* a person-side `peopleUpdated` echo (assumed to carry `lastNoteAt`). Before implementing, the person-side assumption was investigated and **confirmed false** three independent ways:

1. **Empirical** — creating notes produced no `peopleUpdated` webhook.
2. **FUB API docs** — `peopleUpdated`'s trigger field list excludes note activity; note creation fires only `notesCreated`.
3. **Code** — `lastNoteAt`/`lastActivity` are in neither `SNAPSHOT_FIELDS` nor `PersonDiffComputer`, so even a hypothetical person echo would diff to empty and emit nothing.

With no person event ever produced, the person-side channel is inert. 3e shipped **single-channel** (`note.created` only). Consequences:

- The coordinator's `applyEntityCreateTrackedOnly` keeps its `SideEffectRecorder` callback (over-general for one channel, but zero churn to tested infra — the recorder is a one-line lambda recording the note entry, guarded against a null note).
- **D2 was dropped** — there is no person-side echo to test. Deliberately **no tripwire/disabled test** stands in for it: a unit test cannot detect FUB changing its webhook semantics. The "remember this" job goes to durable docs instead — a code comment at `PersonUpsertService.SNAPSHOT_FIELDS`, known-issue #27, and a Phase 4 exit criterion.
- Note annotation lives in `NoteEmissionService`, **not** the universal `DomainEventEmitter` hook — that hook keys off `payload.changed_fields`, which note webhooks don't carry. The annotation deep-copies the payload on a hit (so it never leaks `source` onto sibling notes in a multi-`resourceId` webhook) and passes the original reference through on a miss.
- **D3 (early-echo race) retained** as a diagnostic: if the `notesCreated` webhook beats the `createNote` POST response, the tracker record doesn't exist yet and the echo emits unannotated. Documented, not fixed (a content-hash key would close it; deferred until observed). Made deterministic by resetting the fake client's note-id sequence per test.

Full narrative and the three-way confirmation: [`phase-3-plan.md`](./phase-3-plan.md) §3e + the 2026-06-01 changelog.

## Cross-cutting notes

- **The smoking-gun lock discipline holds for all scalar wraps.** A2 (3b) proves the row lock is released before the FUB call via `REQUIRES_NEW`; 3c reuses the same coordinator path, so it inherits the proof rather than re-testing it.
- **No production behaviour changed.** `agent_followup_enforcement` still runs on the old webhook-shaped trigger and consumes no events. The wraps are invisible until Phase 4.
- **Phase 4 prerequisites surfaced here:** (a) the in-memory tracker's crash-window must be re-evaluated (Redis-backed impl) once a real consumer exists; (b) `notesCreated` must be added to `config/fub-webhook-events.txt` and webhooks re-synced for the note channel to deliver live (blocked on restoring prod FUB credentials, found invalid 2026-06-01); (c) verify the #27 note-trigger filter against real traffic.

## Repo decisions impact

`No` — feature-internal. The coordinator op-mode pattern and gated-emission marker remain local conventions. Promote to `Docs/repo-decisions/` only if a future feature replicates them.
