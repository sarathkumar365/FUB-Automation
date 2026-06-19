# Reporting v1 — scope & guiding questions

> **Status: research only. Candidate scope, not a committed plan.**
> Captures what the user wants v1 of reporting to cover. Nothing here is designed or
> implemented yet — these are the pillars to plan against.

## What v1 covers

1. **The redesigned operations dashboard** — the "is the pipeline healthy right now?"
   overview. The data it needs is inventoried in
   [Dashboard Reporting Needs](./dashboard-reporting-needs.md). This is the first slice.

2. **The accountability / lead-lifecycle questions** below — the reporting that answers
   how leads move through the platform and whether agents follow up.

## Guiding questions (the pillars)

These are the questions v1 reporting should be able to answer. Stated as the user
framed them; definitions (what exactly counts as "contacted", the time window, etc.)
to be nailed down at planning time.

### Q1 — How many leads came in today?
Count of leads that entered the platform (lead intake), for a given period (e.g. today).

### Q2 — How many leads were contacted?
A lead comes into the platform; we have its information. It is either already assigned
to an agent, or an agent self-assigns it. **Of these leads, how many were actually
contacted by an agent?**

### Q3 — Per-agent assignment vs. follow-up (accountability)
For a given agent: how many leads were **assigned to them** *or* **self-assigned by
them**, and of those, **how many did that agent actually follow up on?**

This is the accountability angle — it ties a lead's outcome back to the responsible
agent, surfacing assigned-but-never-followed-up leads (the exact gap the platform's
automation exists to close, and which FUB's own reporting hides).

### Q4 — Assigned → contacted → *useful* (per agent)
For agent A: how many leads were assigned, how many of those did A actually contact,
**and how many of those contacts ended up useful** (a good outcome — qualified,
appointment, deal). Extends Q2/Q3 with an outcome-quality dimension.

### Q5 — Task creation vs. completion (per agent + lead)
For a given agent (and/or lead): how many tasks were **created**, and how many were
**completed**?

### Q6 — Was a due task finished?
A task was created for the agent due in X time. **Did the agent finish it** (on time /
at all)? The per-task accountability version of Q5.

> **Data-availability note (as of 2026-06-19).** Established during the data-source
> deep-dive (this session; not yet written up as its own note):
> - **Answerable today:** assigned → contacted (Q2, and the first two parts of Q4) —
>   from the `events` diary (assignment history) + `processed_calls` (contact).
> - **Blocked today — needs new data capture:**
>   - **Task completion (Q5, Q6)** — no task table, no task webhooks subscribed, no
>     completion signal stored. FUB *does* offer `tasksCreated/Updated/Deleted` webhooks
>     + incremental task pull, so this is capturable (mirror approach under discussion).
>   - **"Useful" outcome (Q4)** — no qualified/converted/useful concept stored today;
>     candidate signals are FUB `appointmentsCreated` / `dealsCreated` (same mirror).
>   - Today we only capture **engine-created** tasks (in `workflow_run_steps.outputs`
>     JSON), not human-created ones.

## Framing — the diagnostic funnel

The three pillars trace one funnel: **leads in → contacted → followed up / converted.**
The value of reading them *together* is diagnostic — the stage where the numbers fall
off tells you *which* problem you have:

- Leads high, but **contacted** low → a **follow-up / speed-to-lead** problem.
- Contacted high, but converted/appointments low → a **conversion** problem.

This is the answer FUB can't give, because it doesn't aggregate across the automation
layer the way we do.

### Pillars mapped to industry metrics

Each pillar lines up with a standard real-estate-team metric, which gives us
benchmarks and a definition to borrow:

| Pillar | Industry metric | Notes / benchmark |
|---|---|---|
| Q1 — leads in | Lead intake / top-of-funnel volume | Plain count; the funnel's denominator. |
| Q2 — leads contacted | **Contact rate** + **speed-to-lead** | Industry leak is huge: only ~27% of leads ever get contacted. First contact <5 min ≈ 21× more likely to convert vs. 30 min. |
| Q3 — assigned vs. followed up | **Agent follow-through rate** + **response time per agent** | The accountability metric. Extends naturally to touches-per-lead (6+ attempts ≈ 70% higher conversion) and "who's slowest to claim". |

### Definition traps (flagged, not resolved)

Borrowed from a real incident in
[`agent-followup-enforcement/plan.md`](../agent-followup-enforcement/plan.md) (Run 163):
a lead was treated as "not followed up" when the agent *had* called 33 min earlier —
outside the workflow's 5-min lookback. The lesson for reporting: **"contacted" and
"followed up" are definition traps.** Which call types count (inbound vs. outbound,
connected vs. missed), and over what window, materially changes the numbers.

### Adjacent questions (candidate v2 — not v1)

Already gestured at in [`ideas.md`](../../product-discovery/ideas.md); parked here so
they aren't lost:

- Speed-to-lead distribution (not just average).
- Appointment-set rate and appointment → client conversion (the next funnel stages).
- Lead-source performance — which sources actually produce closings.
- SLA compliance over time (trend, by week/month).
- Call-outcome breakdown — MISSED / SHORT / CONNECTED across agents and time.

## To resolve at planning time (not now)

- Precise definitions: "contacted", "followed up", "came in" — which events/fields signal each.
- Time windows and timezone handling.
- Whether these are platform-wide totals, per-agent breakdowns, or both.
