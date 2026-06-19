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

## To resolve at planning time (not now)

- Precise definitions: "contacted", "followed up", "came in" — which events/fields signal each.
- Time windows and timezone handling.
- Whether these are platform-wide totals, per-agent breakdowns, or both.
