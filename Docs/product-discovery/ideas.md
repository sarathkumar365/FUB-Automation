# Ideas & Future Features

A freeform scratchpad for product ideas, future directions, and "would be nice" items. Nothing here is committed or prioritized — just captured so it doesn't get lost.

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
