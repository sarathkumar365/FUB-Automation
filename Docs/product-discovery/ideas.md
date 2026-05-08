# Ideas & Future Features

A freeform scratchpad for product ideas, future directions, and "would be nice" items. Nothing here is committed or prioritized — just captured so it doesn't get lost.

---

## Idea: Stale-assignment guard for follow-up workflows

**Date:** 2026-05-08

**The problem:**
Workflows like `agent_followup_enforcement` fire on every `peopleUpdated` webhook for an assigned lead. That means a `peopleUpdated` triggered by an unrelated edit hours or days *after* the original assignment (custom field tweak, tag added, lender attached, etc.) spawns a fresh follow-up run on a stale assignment. The 3-min check fails (no recent call), the workflow posts a nudge note, and 30 minutes later it reassigns or sends to POND — even though the lead was assigned long ago and the agent has every right to not be calling them right now.

**This is a different bug from over-firing in general:** even a single legitimately-redundant trigger on a stale assignment causes a false-positive escalation. The fix-#21 lookback anchor doesn't help because there's no recent call to find — the assignment is the stale fact.

**What this would add:**
A first-class concept of "assignment freshness." Two ways it could manifest:

- **Trigger-level guard:** filter expression that compares `lead.assignedAt` against `now`, only fires when assignment happened recently
- **Workflow-level gate:** an early `branch_on_field` step that reads `lead.assignedAt` (or a derived `lead.minutesSinceAssigned`) and short-circuits to terminal if the assignment is older than a threshold

**Why this is deferred:**
- Requires `lead.assignedAt` exposed via the lead snapshot (need to verify whether FUB's person record includes a stable assignment timestamp — `created` is wrong, `updated` drifts on every edit)
- Could be subsumed by the broader change-detection idea (`lead.previous.*`) — if we know `lead.previous.assignedUserId` was already set to the same value, the assignment isn't fresh
- `agent_followup_enforcement` is the only workflow affected today; in dev the noise is tolerable; in production this becomes urgent

**Triggers worth picking this up:**
- The first time an operator sees a nudge note posted on a lead that was assigned days/weeks ago because an unrelated field edit fired the workflow
- When change-detection (`lead.previous.*`) lands — this becomes a one-line filter expression
- When `agent_followup_enforcement` graduates from dev to production

**Sketch when picked up:**
- Confirm FUB exposes a stable per-assignment timestamp in the person record (or fall back to comparing `lead.previous.assignedUserId` once change-detection is in)
- Add `lead.assignedAt` (and/or `lead.minutesSinceAssigned`) to the `LeadSnapshotResolver` output
- Document a recommended pattern for follow-up workflows: gate node like `$millis() - $toMillis(lead.assignedAt) < 5 * 60 * 1000` to skip stale assignments

---

## Idea: Per-step `since` anchor for `wait_and_check_communication`

**Date:** 2026-05-08

**The problem (when it becomes real):**
Today the step anchors its detection window to `runStartedAt`, so every check in a run looks at the same time range from run start onwards. This is correct for "did anything happen since this workflow began?" — every existing workflow uses it that way. It can't express "did anything happen *since the previous check ran*?" — i.e. multi-stage nurture workflows that check in hourly/daily and want each check to only see new activity.

**What this would add:**
- New optional config field on `wait_and_check_communication`: `since` — a JSONata expression evaluated against `ExpressionScope` to produce the window's lower bound
- Default behavior unchanged: when `since` is omitted, anchor to `runStartedAt` minus the buffer (today's behavior)
- Authors who need per-step anchoring write `"since": "{{ steps.previous_check.completedAt }}"` or any JSONata expression that resolves to an `OffsetDateTime`

**Sketch when picked up:**
- Resolve `since` via the existing `ExpressionEvaluator` against the step's `ExpressionScope`
- If `since` resolves, use it directly as the window's lower bound (skip the `max(lookbackMinutes, BUFFER)` math)
- If `since` is absent or evaluates to null, today's runStart-anchored logic applies
- For the JSONata side: needs `steps.<id>.completedAt` exposed in scope (today only `steps.<id>.<output>` is — would need a small `ExpressionScope` extension)

**Why deferred (no current workflow needs it):**
- All existing workflows (`lead_ai_call_followup`, `agent_followup_enforcement`) want runStart-anchored detection — no per-step anchoring needed
- No roadmap workflow has been concretely scoped that requires "since previous check" semantics
- Adding the config flag now means designing for a hypothetical use case and risking the wrong shape; better to wait for a real driver

**Triggers worth picking this up:**
- A multi-stage nurture workflow lands that wants hourly/daily check-ins where each check should only see *new* activity since the prior check
- An SLA-tracking workflow needs to detect activity in non-overlapping windows

---

## ⭐ IMPORTANT — Idea: Change-detection in trigger filters (`lead.previous.*`)

**Date:** 2026-05-07

**Priority:** Important — first concrete need surfaced from the agent-followup-enforcement workflow (Phase 5 was skipped because of this gap; tracked as known-issues #20). Will block any workflow that needs to fire on a *transition* rather than on every webhook of a given type.

**The problem:**
FUB sends a single generic `peopleUpdated` event for every field change on a lead — assignment, stage, tags, lender, custom fields, etc. There's no way today to write a trigger that fires only when **the assignment specifically changed** (or "stage moved to Hot," "tag X was added," etc.). Workflows must either fire on every `peopleUpdated` (false positives, wasted runs) or rely on per-purpose trigger classes (one per "interesting" change × one per CRM — combinatorial explosion).

**The proposal:**
Expose the lead's pre-webhook state in the JSONata scope alongside the post-webhook state, so workflow authors can write change-detection predicates as one-line filter expressions:

```json
"trigger": {
  "type": "fub_webhook",
  "filter": "lead.assignedUserId != lead.previous.assignedUserId"
}
```

Other examples it unlocks:
- `"lead.stage = 'Hot Lead' and lead.previous.stage != 'Hot Lead'"` — moved into Hot
- `"lead.assignedLenderId and not lead.previous.assignedLenderId"` — lender just attached
- `"lead.assignedUserId and not lead.previous.assignedUserId"` — newly assigned (was unassigned)

**Why this shape (not per-purpose trigger classes):**
- One uniform mechanism scales to N change-types without N new trigger classes
- CRM-agnostic — every CRM upserts with old/new state available, so adapters honor the same scope contract
- Resolves [known-issues #17](../engineering-reference/known-issues.md) (lead.* in trigger filters) in the same change

**Sketch when picked up:**
- `LeadUpsertService` captures the existing `LeadEntity` snapshot before applying the upsert; stashes it transiently on `NormalizedWebhookEvent`
- `RunContext` gains a `previousLead` field
- `ExpressionScope` exposes `lead.previous.*` (or top-level `previousLead`)
- Trigger evaluator's filter scope is unified with step-execution scope (single source of truth)
- Optional convenience: `lead.changedFields` as a Set<String> of top-level keys whose values differ — defer until a real driver

**Honest open questions:**
- **Concurrency:** racing webhooks for the same lead can corrupt "previous" without row-level locking on upsert. Real bug the day this ships.
- **New-lead semantics:** is `lead.previous` `null`, `{}`, or an all-null skeleton? Each choice breaks a different filter. Pick one, document it.
- **JSONata fails open** — a typo (`lead.previousAssignedUserId`) silently evaluates to "always different" and over-fires. No compile-time check.
- **Filter testability** — JSONata predicates inside workflow JSON have no test harness today.

**Effort estimate:**
~3–4 days of focused work (RunContext + ExpressionScope + LeadUpsertService + NormalizedWebhookEvent + trigger eval unification + concurrency story + tests). Compares to ~1 day for a one-off targeted trigger class per use case.

**Triggers worth picking this up:**
- A second concrete workflow needs change detection (stage transition, tag addition, lender attached, etc.)
- We're about to start building any workflow that needs "fire only on a transition"
- We integrate a second CRM and don't want to duplicate per-purpose trigger classes per CRM

**Compared alternatives** (tracked in conversation history; full table available on request):
| Approach | Verdict |
|---|---|
| **A.** Targeted trigger class per change | Boring/safe; wins if change-detection is a one-off |
| **B.** Scope-based diff (`lead.previous.*`) — *this idea* | Elegant; scales; wins if change-detection is recurring |
| **C.** Faceted/synthetic events at ingest | Avoid — pushes workflow concerns into ingestion |
| **D.** Gate inside workflow on every update | Cheap to build, expensive to run |
| **E.** Filter-only without diff | Cannot solve the problem |

The decision between A and B hinges on whether change-detection is a recurring category. Today: not enough signal — agent-followup-enforcement is the only concrete need, and we're shipping it in dev with an over-firing trigger to gather signal first.

---

## Idea: Per-workflow `config.*` namespace + JSONB column

**Date:** 2026-05-07

**The problem (when it becomes real):**
A workflow's step configs sometimes need operator-tunable constants — ISA user ID, unorganic pond ID, retry windows, SLA thresholds — that are chosen per workflow at creation time, not derived from runtime data. Today these can be hardcoded as literal values in step configs (which works fine when each value is referenced exactly once, as in the agent-followup-enforcement workflow), but as soon as the same value is referenced from multiple step configs in the same workflow, hardcoding becomes a DRY problem.

**What this would add:**
- New `config JSONB DEFAULT '{}' NOT NULL` column on `automation_workflows`
- Workflow JSON gains an optional top-level `config` block (free-form per-workflow tunables)
- `RunContext` carries a `config` field; `ExpressionScope` exposes it under the `config` top-level key
- Step configs reference `{{ config.foo }}` via the standard JSONata templating
- Admin POST/PUT endpoints accept and persist the `config` block; GET returns it

**Sketch when picked up:**
- Mirror the same metadata-build pattern Phase 1's `lead.*` and Phase 3's `now.*` use — resolve at `buildRunContext` time, expose via `ExpressionScope` as a pure mapper
- Per-step lookup of the workflow entity's `config` (one indexed query, same auto-fresh property as `lead.*`)
- Validation deliberately deferred — workflow author owns the config shape until a real validation requirement appears
- Trigger-filter scope deliberately omitted (same reasoning as known-issues #17 for `lead.*` and `now.*`)

**Why deferred (Phase 4 of agent-followup-enforcement was originally scoped for this and was dropped):**
- The driving use case (agent-followup-enforcement) only references each tunable value **once**, in one step. Literal hardcoded values in the step config work cleanly with no DRY tax.
- We don't have an editable-config UI; operators PUT updated workflow JSON via the existing admin endpoint either way.
- Validation is speculative without a concrete requirement.
- Total v1 effort would have been ~1–2 days, all of which is now deferrable until a real driver appears.

**Triggers worth picking this up:**
- A new workflow has the same value referenced from **3+ steps**
- An editable Settings UI is in scope and we want a clean separation between "graph structure" and "tunable values"
- Per-tenant overrides become real (tied to the multi-CRM idea above)

---

## Idea: Expose `lead.*` (and other run-context namespaces) in trigger-filter scope

**Date:** 2026-05-07

**The problem:**
The agent-followup-enforcement Phase 1 work adds a `lead.*` namespace to the **step-execution** JSONata scope, so workflow authors can write `{{ lead.assignedUserId }}` in step configs. But the **trigger-filter** scope ([FubWebhookTriggerType.java:79](../../src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java)) is built separately and does NOT include `lead.*`. So a trigger filter can't yet say "fire only if the lead is in stage 'Hot Lead'" or "skip if the lead is already tagged DNC."

**Why this is deferred (not done now):**
- Trigger-filter eval runs on every active workflow's filter for every inbound webhook. Adding a DB read per filter eval has a different cost profile than a step's per-step DB read.
- The webhook payload itself often carries enough info for current trigger filtering (`event.payload.eventType = 'peopleCreated'` etc.). Most workflows don't need persistent lead state at trigger time.
- Scope shape consistency between trigger-filter and step is nice but not strictly required.

**What this would unlock:**
- Lead-stage gating: `"filter": "lead.stage = 'Hot Lead'"`
- Tag-based skips: `"filter": "$contains(lead.tags, 'DNC') = false"`
- Source filtering: `"filter": "lead.source = 'Zillow'"`
- Branching workflows by attribute without needing a separate `branch_on_field` step at the start of every run

**Sketch when picked up:**
- Either share the per-step `RunContext`-style metadata-build with the trigger evaluator (best — single source of truth for the scope shape), or copy the resolution logic into `FubWebhookTriggerType`.
- Cache the snapshot per webhook-event-id so N active workflows hitting the same lead share one DB read.
- Decide whether `lead.*` is opt-in via a trigger config flag, or always on.

---

## Idea: CRM-agnostic event vocabulary (multi-CRM support)

**Date:** 2026-05-07

**The problem:**
Today the automation engine is FUB-only. The internal event model (`NormalizedDomain`, `NormalizedAction`) and clients (`FubFollowUpBossClient`) assume FUB. As we plan to integrate other real-estate CRMs (HubSpot, Salesforce, Pipedrive, GoHighLevel, etc.), the FUB-flavored vocabulary will start leaking into places it doesn't belong — workflow JSON, the admin UI, internal docs.

**The opportunity:**
Establish a **CRM-agnostic event vocabulary** at the boundary, so each CRM is just an adapter that translates its own terminology into our normalized model. Workflow authors write workflows once; they fire regardless of which CRM provides the source events.

**Different CRMs, same concept:**

| CRM | "Lead/Contact" path | "Team member" path | "Call" path |
|---|---|---|---|
| Follow Up Boss | `/v1/people` ("person") | `/v1/users` | `/v1/calls` |
| HubSpot | `/contacts` ("contact") | `/users` (or `/owners`) | `/engagements` |
| Salesforce | `/Lead` + `/Contact` | `/User` | `/Task` (call subtype) |
| Pipedrive | `/persons` | `/users` | `/activities` |
| GoHighLevel | `/contacts` | `/users` | `/conversations` |

If our enum is `PERSON` (FUB's term), every other adapter mentally translates. If it's `LEAD` (our business-domain term), the translation is natural everywhere.

**Proposed canonical taxonomy:**

| Domain | Meaning | Common actions |
|---|---|---|
| `LEAD` | The prospect/contact a workflow operates on | `CREATED`, `UPDATED` |
| `CALL` | Phone call activity | `CREATED` |
| `NOTE` | Notes attached to a lead | `CREATED`, `UPDATED` |
| `TASK` | Tasks attached to a lead | `CREATED`, `UPDATED`, `COMPLETED` |
| `DEAL` | Sales opportunity (CRMs that distinguish from lead stage) | `CREATED`, `UPDATED`, `WON`, `LOST` |
| `APPOINTMENT` | Calendar event | `CREATED`, `UPDATED`, `CANCELLED` |
| `USER` | Team member events (may not have webhooks in most CRMs) | TBD |
| `UNKNOWN` | Fallback | — |

**Architecture sketch (for when this is picked up):**

- Per-CRM webhook parser implementations behind a `CrmEventNormalizer` interface; current `FubWebhookParser` becomes one of many
- Per-CRM clients live under `integration/<crm>/` (already started: `client/fub/`)
- `NormalizedDomain` / `NormalizedAction` enums and `ExpressionScope` stay CRM-neutral
- A registry of installed CRM adapters + per-tenant CRM selection (multi-tenancy implication)
- Workflow JSON references domains/actions only — never CRM-specific event types
- Step types that touch a CRM (e.g. `fub_create_note`, `fub_reassign`) split into:
  - Generic step: `crm_create_note`, `crm_reassign` — dispatched to the active CRM adapter
  - Or keep CRM-prefixed steps and have the workflow author choose by CRM (more explicit, less magic)

**First concrete step:**
The `agent-followup-enforcement` feature (in progress) lays groundwork by renaming `NormalizedDomain.ASSIGNMENT` → `LEAD` in its Phase 0. After that ships, this idea can be picked up: introduce the parser interface, add a second CRM adapter, and decide the generic-vs-prefixed step convention.

**Why now-ish (not way later):**
- `NormalizedDomain` enum values are stored in DB rows (`webhook_events.normalized_domain`) and will be referenced in workflow JSON. Rename cost grows the longer we wait.
- Selling automation to a team that's on HubSpot is a non-starter as long as we're FUB-only.
- The architecture is still simple enough to refactor (single CRM, modest code volume) — the multi-CRM seam is much easier to introduce now than after a second concrete CRM has bolted on alongside FUB.

---

## Idea: Analytics Platform for Real Estate CRMs

**Date:** 2026-04-08

**The problem:**
FUB and most other real estate CRMs offer weak or shallow analytics. What's available is generic, hard to customize, and doesn't surface the signals that actually matter to a real estate team — lead response behavior, agent performance, SLA compliance, pipeline conversion, etc.

**The opportunity:**
Because we sit between FUB (or any CRM) and the team's operations, we see data that the CRM itself never aggregates:
- Every webhook event (calls, assignments, people changes)
- Every automation run and its outcome
- Every SLA violation or compliance
- Every call decision made by the engine

This puts us in a unique position to build analytics that no CRM dashboard can offer — because we own the event history.

**Potential analytics surfaces:**

- **Agent performance** — response time per agent, SLA hit rate, call follow-through rate
- **Lead funnel** — how many leads were claimed, contacted, converted vs. dropped off
- **SLA compliance over time** — trend charts of policy compliance rate by week/month
- **Call outcome breakdown** — distribution of MISSED / SHORT / CONNECTED across agents and time
- **Webhook volume trends** — event rates, anomaly detection (sudden drop = integration issue)
- **Assignment patterns** — which agents get the most leads, who is slowest to claim
- **Policy effectiveness** — are the automation policies actually improving response time?

**Why this is defensible:**
CRMs can only report on what's in their own data model. We aggregate across the automation layer, which means our analytics reflect *actual behavior* — not just what got logged in the CRM. A lead that was assigned but never called won't show up as a problem in FUB. It will show up in ours.

**Notes / open questions:**
- Need to decide: embedded charts in existing pages vs. a dedicated `/analytics` section
- Time-series data will need aggregation — either a dedicated analytics schema or pre-computed summaries stored on a schedule
- Could eventually expose this as a report export (PDF/CSV) for team leads

---

## Idea: (from product design analysis) — items to revisit

- **Webhook sync button in Settings** — wire up the stubbed `registerWebhook()` in `FollowUpBossClient` to let admin push webhook registrations to FUB from the UI
- **Policy action target field** — the `ON_FAILURE_EXECUTE_ACTION` step has a target field marked "coming soon" — needs a decision on what reassignment targeting looks like
- **Lead Lookup** — once `sourceLeadId` parser fix lands for assignment events, the related webhooks section of Lead Lookup becomes fully accurate
- **Config editing in Settings** — start read-only, explore making call rule thresholds editable at runtime without restart
