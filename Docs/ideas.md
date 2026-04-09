# Ideas & Future Features

A freeform scratchpad for product ideas, future directions, and "would be nice" items. Nothing here is committed or prioritized — just captured so it doesn't get lost.

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
