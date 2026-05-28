# Phase 2 — Implementation Log

Status: `IN PROGRESS` — sub-phase 2a underway.

This file is the decision narrative for Phase 2 per the AGENTS.md convention: it answers *"why was it built this way?"* for a future reader, not *"what files changed"* (git is the source of truth for that). Entries are added as each sub-phase lands.

See [`phase-2-plan.md`](./phase-2-plan.md) for the locked commit-level plan and [`plan.md`](./plan.md) for architectural rationale.

---

## Sub-phase 2a — Scaffold

### `DomainEventEmitter` — what it does, in plain English

Every place in the codebase that genuinely changes state — a person got reassigned, a call came in, a note was created — calls `DomainEventEmitter.emit(...)`. Think of it as the engine saying out loud: **"this just happened, and it really happened."**

The emitter does two things, in this exact order:

1. **Writes the event into the `events` table** inside the same database transaction as the state change that caused it. So either *both* the state change and the event are saved, or *neither* is. There's no universe where the person's `assignedUserId` updates but the `person.state_changed` event goes missing — or vice versa.
2. **Tells the rest of the engine about it, but only after the database confirms the change is locked in.** It posts a "when this transaction successfully commits, run the dispatcher" note. If the transaction rolls back, the note is thrown away and nobody hears about the event — because it didn't really happen.

### Benefits

| Benefit | Plain English |
|---|---|
| **Atomicity** | The event row and the state change are inseparable. You can't get one without the other. No more "the database says X but our audit log says Y." |
| **No phantom events on rollback** | If the upsert fails halfway through and rolls back, no listener ever hears about a change that never happened. Eliminates a whole class of ghost workflow runs. |
| **No locked-row contention with listeners** | Listeners (Phase 4's workflow trigger) run *after* the transaction commits, so they can take their time — they're not holding the `persons` row hostage while they decide what to do. |
| **Loud failures, not silent ones** | If a future developer adds a new caller and forgets to mark it `@Transactional`, the code throws an exception the first time it runs. The bug surfaces in dev, not in production three months later as a missing audit trail. The `MANDATORY` propagation guard is what enforces this. |
| **One central choke point** | Every domain event in the system flows through this one method. Want to add metrics, tracing, an outbox poller, schema validation? One place to change. |

### The enabling tech — one-liner

> Spring's **`TransactionSynchronizationManager`** lets you attach a callback that fires only *after* the current database transaction commits — so the emitter can record the event inside the transaction and notify listeners outside it, atomically.

Without this single piece of framework machinery, you'd have to choose between:
- **Notify inside the transaction** — slow, locks held too long, listener failures roll back the state change
- **Notify outside the transaction** — fast, but you can lose the event if the process dies between commit and notify

The synchronization hook gives you both halves of the deal: the event row is durable before any listener runs, and the listener runs outside the lock-holding window of the upsert.

### Naming caution

Three related-but-distinct concepts live in this codebase. Easy to confuse on first read:

| Class | What it is |
|---|---|
| `WebhookEventEntity` (table `webhook_events`) | The raw inbound webhook from FUB. Pre-existing. |
| `EventEntity` (table `events`) | The persisted domain event the engine emits when state actually changes or an append happens. NEW in Phase 2. |
| `DomainEvent` (Java record) | The in-memory shape handed to listeners after commit. Same data as `EventEntity`, but a plain value type so listeners don't depend on JPA. |

`EventEntity.sourceEventId` is the FK linking each domain event back to the webhook that caused it (null for engine-synthesized events).

### Repo decisions impact

To be assessed at the end of sub-phase 2e. Likely `No` — feature-internal.
