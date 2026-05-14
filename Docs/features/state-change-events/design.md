# State-change events — SUPERSEDED

This design has been superseded by a broader reframe.

**Current architecture:** [`Docs/features/domain-events/plan.md`](../domain-events/plan.md)
**Phase plan:** [`Docs/features/domain-events/phases.md`](../domain-events/phases.md)

## What changed and why

After deliberation against the field observations in [`Docs/features/agent-followup-enforcement/field-observations.md`](../agent-followup-enforcement/field-observations.md), the original "layered fix" framing here (Layer 0/1/2/3/5 over the existing webhook-as-trigger model) was reframed as a model change rather than a patch stack:

- The engine now treats webhooks as **observations of state**, not as discrete events. Diffing happens at upsert; an event is emitted only if something actually changed.
- The data model generalised from "state-change events" specifically to a unified `events` table that also handles **append** entities (calls, notes, future entity kinds). State-change is one `event_kind`; append is another.
- The phase ordering changed: local-state-first writes land before the trigger schema refactor (otherwise the deployment window briefly makes the system worse than today).

References elsewhere in `Docs/` that pointed here have been updated to the new path. This file remains only as a redirect for stable inbound links.
