# RD-006: Engine-echo exclusion is safe-by-default, two-level gated

## Status
Accepted (2026-06-03). Enforcement lands in the domain-events feature, **Phase 4d**; the provenance annotation it reads (`event.origin`) shipped in Phase 4b.

## Context
The engine writes back to Follow Up Boss (reassign, move-to-pond, add-tag, create-note). FUB echoes each write back as a webhook, which the engine would otherwise react to — triggering a workflow on its **own** action. This self-trigger loop is [known-issue #23](../engineering-reference/known-issues.md) and a major contributor to the historical ~50% bad-run rate.

Phase 3 made every engine-originated write detectable: the write is recorded on `EngineWriteTracker`, and the resulting event is annotated `source = "ENGINE"`. Phase 4b surfaces that annotation in trigger scope as **`event.origin`** (`"ENGINE"` for engine-caused events, `"EXTERNAL"` otherwise — always present).

The open question was **how a workflow avoids reacting to engine echoes.** The original design (`plan.md`, pre-2026-06-03) was **opt-in**: each workflow author had to add `event.origin != 'ENGINE'` to their filter, and the validator would *warn* if they forgot. That carries a real, silent failure mode — a forgetful author re-introduces #23 with no error. Making the unsafe thing the default-on-omission is the wrong polarity for a platform guard.

## Decision
Engine-caused events are **excluded from every workflow by default**, enforced by the platform — not by an author-written filter predicate. A workflow reacts to engine-caused events **only when both** gates are open:

1. **Global capability** — a platform-level switch ("is this system allowed to let workflows consume engine events at all?"), entitlement/"license" shaped. **Default OFF.** Implemented as a config flag today (single-tenant, dev); shaped to become a per-tenant entitlement later.
2. **Per-workflow opt-in** — `reactToEngineEvents: true` in the workflow's trigger config. **Default OFF.**

```
reacts-to-engine-event  =  globalCapability  AND  workflow.reactToEngineEvents
```

External (non-engine) events are unaffected — they always evaluate normally. This gate only suppresses events with `event.origin = 'ENGINE'`. The default posture is doubly safe: even if an author flips the per-workflow flag, nothing happens unless the global capability is also on.

### Why two levels
- **Per-workflow flag** gives authors control for the rare workflow that legitimately reacts to engine actions (e.g. an audit/notification workflow).
- **Global capability** keeps the whole behavior gated behind an explicit platform/tenant entitlement, so reacting-to-engine-events is never available by accident — "even if it's turned on in the workflow, it shouldn't react unless the platform allows it."

### Enforcement and ergonomics
- **Enforced at trigger evaluation** (Phase 4d): the router/trigger reads `event.origin` and skips engine-caused events for workflows that have not opted in (with the capability on), *before* the workflow's filter runs.
- **Authors do not write an echo-exclusion predicate.** The standard filter is just the state/change condition, e.g. `person.kind = 'LEAD' AND change.assignedUserId.changed`. `event.origin` remains available in scope, so an opt-in workflow can still discriminate (e.g. react *only* to engine events with `event.origin = 'ENGINE'`).
- **Debuggability:** when the platform skips an engine-caused event for a non-opted-in workflow, it logs it (`skipped engine-caused event workflowKey=… reactToEngineEvents=false`) so "why didn't my workflow fire?" is traceable, not silent.

### Distinct from `engine.write.emit-events`
`engine.write.emit-events` controls whether the engine **emits** its own events (so they are recorded/visible for audit and future consumers). This decision controls whether workflows **consume** them. The intended posture is emit **ON** (audit trail) + consumption-gate **OFF** (nobody reacts by default) — the two are complementary, not redundant.

## Impact
- **Validator (Phase 4c):** no engine-echo warning is needed (there is no author predicate to forget). The validator simply accepts the optional `reactToEngineEvents` boolean in the trigger config. The "warnings channel" question is moot.
- **Production filter:** `agent_followup_enforcement` drops `event.origin != 'ENGINE'` from its filter (platform-enforced) → `person.kind = 'LEAD' AND change.assignedUserId.changed`.
- **New trigger-config field:** `reactToEngineEvents` (boolean, default false), stored in the trigger config blob — no schema migration.
- **New platform config:** a global engine-event-consumption capability flag (default OFF).
- **Future workflow-authoring UI** should surface `reactToEngineEvents` only when the global capability is on, and make the default-exclusion behavior discoverable.

## Applies To
- The domain-events trigger pipeline (`DomainEventTriggerType`, `route(DomainEvent)`, trigger evaluation).
- Workflow authoring (trigger config contract).
- Future multi-tenant entitlement model (the global capability becomes a per-tenant license).

## Supersedes / Superseded By
- **Supersedes:** the opt-in `event.origin != 'ENGINE'` echo-exclusion default previously described in [`Docs/features/domain-events/plan.md`](../features/domain-events/plan.md) "Engine-echo exclusion."
- **Superseded by:** none.

## See Also
- [`Docs/features/domain-events/plan.md`](../features/domain-events/plan.md) — "Engine-echo exclusion" (canonical model), expression scope (`event.origin`).
- [`Docs/features/domain-events/phases.md`](../features/domain-events/phases.md) — Phase 4; [`phase-4-plan.md`](../features/domain-events/phase-4-plan.md) — 4c (validator), 4d (enforcement).
- [`Docs/engineering-reference/known-issues.md`](../engineering-reference/known-issues.md) — #23 (self-trigger echo), #27/#28 (note-channel echo specifics).
