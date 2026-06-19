# Reporting Platform — Research

> Findings, prior art, and constraints gathered before design. Feeds `plan.md`.
>
> **State: research only. No plan or phases exist yet — nothing is implemented.**

## Context

_TBD_

## Existing state

_TBD_

## Research notes

- [Reporting v1 — scope & guiding questions](./v1-scope.md) — candidate v1 scope: the
  redesigned dashboard plus the three lead-lifecycle / agent-accountability questions.
  Research only, not a committed plan.
- [Dashboard Reporting Needs](./dashboard-reporting-needs.md) — what reporting data
  the redesigned operations dashboard would need (one slice of the platform, not the
  feature itself). Derived from the existing design handoff; no plan attached.
- [Architecture direction (learnings)](./architecture-direction.md) — the shape settled
  during the brainstorm: a FUB mirror as the capture spine, one read model, two query
  subsystems (deterministic SQL + Vanna text-to-SQL). Records why reporting-as-a-live-agent
  was rejected and the scope-narrowing decisions (historical-first, on-time dropped).
- [findings/data-model.md](./findings/data-model.md) — ground truth on the data we have
  today (`events`, `processed_calls`, `persons`), how it's written, agent-name
  resolution, and the accuracy caveats every report inherits. The factual basis for the
  plan.

The architecture is recorded as
[RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md); the build is in
[plan.md](./plan.md) + [phases.md](./phases.md).

## Constraints & open questions

_TBD_
