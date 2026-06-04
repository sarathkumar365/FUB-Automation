# Domain Events — the plain-language overview

> **Read this first.** It's the map for the whole feature: what the system does, why this feature exists, what it changes, and how the pieces (collapse, echo-suppression, trigger filter, cancel-on-collision) fit together. No prior context assumed. For the formal spec see [`plan.md`](./plan.md); for the phase-by-phase build see [`phases.md`](./phases.md). If this doc and those ever disagree, those win — but this is the one to re-read when you've lost the thread.
>
> Diagrams are Mermaid — open in any Mermaid-capable viewer (GitHub, VS Code preview, IntelliJ).

---

## 1. What the system is

An **automation engine** bolted onto **Follow Up Boss (FUB)**, a real-estate CRM. FUB holds *people* (leads, agents, clients), their *calls*, *notes*, *tags*, and *assignments*. The engine watches what happens in FUB and **runs workflows** in response.

Today one workflow matters: **`agent_followup_enforcement`**. In plain English:

> When a lead is assigned to an agent, wait a few minutes, then check: did the agent actually contact the lead? If **not**, post a nudge note and/or reassign the lead.

That's the product. The engine enforces "agents must follow up fast."

---

## 2. How FUB talks to the engine: webhooks

FUB notifies the engine via **webhooks** — small HTTP messages like `peopleUpdated`, `callsCreated`, `notesCreated`. When a person changes, FUB sends `peopleUpdated`.

The **original design** was literal: *webhook arrives → run the workflow.*

```mermaid
flowchart LR
    FUB([FUB]) -->|peopleUpdated| ENG[Engine]
    ENG -->|run| WF[agent_followup_enforcement]
```

Simple — and that simplicity is exactly what broke.

---

## 3. Why it broke — the bad-run problem

This design produced **46–64% "bad runs"**: workflow runs that should never have happened. Three causes, all from the same root flaw — **the webhook carries no idea of *what* changed.**

```mermaid
flowchart TD
    subgraph causes [Three ways the old design over-fires]
        A["1 · DUMB WEBHOOK<br/>peopleUpdated fires on ANY change<br/>(tag, phone, stage…) and never says which.<br/>Trigger fires on everything."]
        B["2 · ECHO LOOP<br/>Engine reassigns a lead → FUB sends a<br/>peopleUpdated about that change →<br/>engine reacts to its OWN action."]
        C["3 · BURSTS<br/>FUB fires 3–4 peopleUpdated within<br/>seconds for ONE edit → 3–4 parallel runs."]
    end
    A --> BAD["~50% bad runs"]
    B --> BAD
    C --> BAD
```

The **echo loop** is the nastiest — the engine triggering itself:

```mermaid
sequenceDiagram
    participant W as Workflow
    participant FUB
    participant E as Engine
    W->>FUB: reassign lead to Agent B
    FUB-->>E: peopleUpdated (the echo of our own write)
    E->>W: "a person changed — enforce follow-up!"
    Note over W,E: Engine reacts to its own action.<br/>Self-trigger loop.
```

**What the damage looked like in the field:** nudge notes posted on a lead who was mid–11-minute real conversation with their agent; one lead (20235) reassigned to the same person **three times** back-to-back from three parallel runs; runs where the correct count was zero. The system only stayed "safe" by accident — a 5-minute buffer that happened to absorb some over-fires. Not production-credible, and impossible to add a second workflow to without inheriting every bug.

---

## 4. The key insight — the reframe

The entire feature rests on one shift in thinking:

> **A webhook is not an event to react to. It's a *signal that some state might have changed.***

Old way: *three webhooks = three reactions.*
New way: when a webhook arrives, the engine **fetches the current state, compares it to what it already knew (a "diff"), and emits a real event only if something *meaningful actually changed*.**

That turns FUB's noisy webhook stream into a clean, typed stream of **domain events** — the engine's *own* truth about what changed:

- `person.created` — a new person appeared
- `person.state_changed` — **carries exactly which fields changed** (e.g. `assignedUserId: 10 → 11`)
- `call.created`, `note.created` — things that happened

```mermaid
flowchart LR
    FUB([FUB]) -->|"raw webhook<br/>(peopleUpdated)"| DIFF{Engine:<br/>fetch state,<br/>diff vs known}
    DIFF -->|"nothing<br/>meaningful<br/>changed"| STOP([no event])
    DIFF -->|"assignedUserId<br/>10 → 11"| EV["domain event<br/>person.state_changed<br/>{changed: assignedUserId}"]
    EV --> WF[workflows subscribe<br/>to WHAT CHANGED]
```

Workflows now subscribe to **domain events**, not raw webhooks. The trigger goes from *"fire when a person is updated"* to *"fire when `assignedUserId` actually changed, and I didn't cause it."*

This kills all three bugs:

| Old bug | Killed by |
|---|---|
| **Over-fire** (dumb webhook) | The event says *what* changed; the workflow only watches its one field. |
| **Bursts** | First webhook diffs → event; the rest see "no change" → **one** event, not three. *(event collapse)* |
| **Echo loop** | Engine writes update its **local copy first** → FUB's echo diffs to *empty* → no event. And the change is **tagged `source=ENGINE`** so workflows can filter out "things I caused." |

Here's the echo loop **after** the fix:

```mermaid
sequenceDiagram
    participant W as Workflow
    participant E as Engine
    participant FUB
    W->>E: reassign lead to Agent B
    E->>E: update LOCAL copy first (assignedUserId = B)
    E->>FUB: PUT reassign
    FUB-->>E: peopleUpdated (echo)
    E->>E: diff vs local… already B → EMPTY
    Note over E: no event emitted. Loop dies. ✅
```

---

## 5. The promises the feature makes (the invariants)

- **I1** — the engine's local copy mirrors FUB for every field a workflow references.
- **I2** — an event is emitted *only* when something meaningful happened.
- **I3** — the engine never phantom-triggers on its own writes.
- **I4** — at most **one active run** per (workflow, person) at a time.  ← *Phase 5*
- **I5** — every run records both the webhook *and* the logical event that caused it (so it's debuggable).

---

## 6. How it's being built — the phases

Staged so each step is safe and reviewable on its own.

```mermaid
flowchart LR
    P0[0 · Replay harness<br/>✅] --> P1[1 · Foundation<br/>✅]
    P1 --> PR[Pre-2 · Rename<br/>Lead→Person ✅]
    PR --> P2[2 · Events table<br/>+ diff ✅]
    P2 --> P3[3 · Engine writes<br/>local-first + tag ✅]
    P3 --> P4[4 · Workflows<br/>SUBSCRIBE 🔨]
    P4 --> P5[5 · Run-collision<br/>cancel-only ⏳]
    style P4 fill:#fff3cd,stroke:#f0ad4e,color:#000
    style P5 fill:#f0f0f0,stroke:#999,color:#000
```

| Phase | What it does | State |
|---|---|---|
| 0 | Replay harness — replays recorded real incidents to prove fixes | done |
| 1 | Foundation plumbing | done |
| Pre-2 | Rename `Lead`→`Person` (FUB calls them people) | done |
| 2 | Build the events table + diff machinery. Events get **written** on every webhook — but **nothing reads them yet** | done |
| 3 | Engine writes update local state first + tag themselves `source=ENGINE` — the echo-killer | done |
| **4** | **Workflows actually subscribe to domain events.** The old webhook trigger is retired. *The bad-run-rate win lands here.* | **in progress** |
| 5 | Run-collision handling — field-aware supersede-at-plan-time (#29) | done |

**Subtlety worth holding onto:** because of Phase 2, **events are already being written on every webhook right now** — they just pile up with no reader. So Phase 4's real job is **attaching the first reader**. (That's why the order of "attach reader" vs. "turn on the engine's own event emission" matters during cutover.)

---

## 7. The layered defenses — and where Phase 5 fits

After Phases 2–4 there are **several defenses against duplicate/unwanted runs**, each sitting at a different point in the pipeline. This is the part that's easy to tangle — keep the *positions* straight:

```mermaid
flowchart TD
    WH([webhook arrives]) --> L1{1 · COLLAPSE<br/>Phase 2<br/>did anything<br/>meaningful change?}
    L1 -->|no| X1([drop])
    L1 -->|yes| L2{2 · ECHO<br/>Phase 3<br/>did WE cause it?}
    L2 -->|"yes (source=ENGINE)"| X2([filtered out])
    L2 -->|no| L3{3 · FILTER<br/>Phase 4<br/>is THIS the change<br/>the workflow watches?}
    L3 -->|no| X3([ignored])
    L3 -->|yes| L4{4 · COLLISION<br/>Phase 5<br/>run active for this person<br/>on an overlapping change?}
    L4 -->|no| RUN([start run → it acts])
    L4 -->|"yes"| SUP([SUPERSEDE: cancel the stale run<br/>reason=SUPERSEDED_BY_NEWER_EVENT<br/>· the new run proceeds → enforces newest ·])
```

Read top to bottom — each gate answers a different question:

1. **Collapse** *(Phase 2)* — "did anything meaningful change?" Kills bursts (3 webhooks → 1 event).
2. **Echo** *(Phase 3)* — "did *we* cause this?" Tagged `source=ENGINE` → filtered.
3. **Filter** *(Phase 4)* — "is this the specific change the workflow watches?" The front door.
4. **Collision** *(Phase 5, supersede)* — "is a run already active for this person on an *overlapping* change?" If yes → **cancel the stale run; the newer run proceeds and enforces the newest state**.

### What Phase 5 is actually for

After gates 1–3, almost all duplicates are gone. **One case survives:** two *genuinely different, meaningful* changes for the **same lead**, **close in time**.

> Example: lead assigned to **Agent A** → a run starts and begins its wait. 90 seconds later, the lead is reassigned to **Agent B**. Both changes are real. The first run is now waiting to nudge about a stale assignment.

**The decision (2026-06-03): supersede-at-plan-time, field-aware.** When Event 2 arrives it **already spawns its own run** (it passed the filter). So at plan time we cancel the *stale* run (Agent A) and let the *new* run (Agent B) proceed — the engine enforces the **newest** state with exactly one run. The stale run is cancelled before its waited actions fire, so it never acts on Agent-A state; no freshness re-check is needed. **Field-aware:** the cancel fires only when Event 2's `changed_fields` overlap the in-flight run's, so an unrelated change (e.g. a phone edit) never cancels an assignment-premised run.

```mermaid
sequenceDiagram
    participant Ev1 as Event 1 (→ Agent A)
    participant Run1 as Run 1 (stale)
    participant Ev2 as Event 2 (→ Agent B)
    participant Run2 as Run 2 (newest)
    Ev1->>Run1: start, begin wait…
    Note over Run1: waiting (no action yet)
    Ev2->>Run1: SUPERSEDE — cancel (reason=SUPERSEDED_BY_NEWER_EVENT,<br/>domain_event_id=Ev2) — only if changed_fields overlap
    Ev2->>Run2: start (the newer run)
    Note over Ev2,Run2: Run 2 enforces Agent B — the newest state. One run, no stale action.
```

The options we weighed:

| Option | Behavior | Verdict |
|---|---|---|
| Let both run | Two nudge cycles | ❌ the original bug |
| Hard-suppress | Ignore #2, keep #1 | ❌ #1 acts on stale info (Agent A, already replaced) |
| Cancel-only | Cancel #1, start nothing | ⚠️ safe, but #2 (newest) goes unenforced |
| **Supersede** *(chosen)* | **Cancel #1, #2 proceeds** | ✅ one run, enforces the newest state. Free here — #2's run already exists |

**Why supersede (not cancel-only):** under Rail 2 the replacement run already exists (Event 2 created it), so the "supersede ideal" — act once, on the newest state — costs no more than cancel-only, and it *enforces* the newest change instead of dropping it. The freshness re-check the original ideal needed is unnecessary because the stale run is cancelled before it acts. The only residual is a truly-simultaneous race (both runs miss each other) — rare, tracked in known-issue #29.

After this, the domain-events feature is **complete**.

### The words that keep blurring — pinned down

- **Filter** *(Phase 4, built)* = "is this event worth reacting to at all?" — at the front door.
- **Supersede-on-collision** *(Phase 5, built)* = "a newer event whose change-set *overlaps* an in-flight run cancels that run; the newer run proceeds and enforces the newest state." — when runs collide.
- **Freshness re-check** *(not needed / not built)* = "re-read live state right before acting." Unnecessary under supersede because the stale run is cancelled before it acts; would only matter if a cancelled run could have already acted.

---

## 8. One-paragraph summary

The engine enforces agent follow-up. FUB's webhooks are dumb — they say "something changed" but not *what* — so the old "webhook → run" design fired wrongly about half the time, including triggering on the engine's own writes. The fix is to stop treating webhooks as events and start treating them as *signals*: the engine diffs the new state against what it knew and emits a clean, typed **domain event** only when something meaningful changed, tagging its own writes so it never reacts to itself. Workflows subscribe to those events ("fire when `assignedUserId` changed and I didn't cause it") instead of raw webhooks. That removes the over-fires, the bursts, and the echo loop. The last remaining case — two *genuinely different* changes for the same lead racing each other — is handled in Phase 5 by **superseding**: a newer event whose change-set overlaps an in-flight run cancels that stale run, and the newer run (which the event already spawned) proceeds to enforce the newest state. One run, on the freshest data, no stale action.
