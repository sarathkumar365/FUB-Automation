# Lead Management Platform — Documentation

Backend implementation documentation for the Automation Engine's lead management platform. Each file covers a specific area — start with the overview, then dive into any flow.

## Contents

| # | Document | What it covers |
|---|----------|---------------|
| 01 | [Overview](01-overview.md) | What this app is, why it exists, local dev setup, startup workflow |
| 02 | [Architecture](02-architecture.md) | Hexagonal architecture, package structure, high-level system flow diagram |
| 03 | [Decisions and Phases](03-decisions-and-phases.md) | Repo-wide design decisions (RD-001–003), feature phase status |
| 04 | [Configuration and Schema](04-configuration-and-schema.md) | All config properties, database tables, indexes, constraints |
| 05 | [Flow A: Webhook Ingestion](05-flow-webhook-ingestion.md) | POST /webhooks/{source} → signature verification → parsing → dedup → dispatch |
| 06 | [Flow B: Call Automation](06-flow-call-automation.md) | Call processing, decision engine, task creation, retry logic, dev guard |
| 07 | [Flow C: Assignment Policy Planning](07-flow-assignment-policy.md) | Assignment routing, blueprint validation, idempotency, step materialization |
| 08 | [Flow D: Policy Execution](08-flow-policy-execution.md) | Due worker, claim query, transition engine, step executors, compensation |
| 09 | [Flow E: FUB Client](09-flow-fub-client.md) | REST API client interface, adapter, exception mapping, retry wrapper |
| 10 | [Flow F: Admin APIs](10-flow-admin-apis.md) | All admin endpoints, pagination, SSE live feed, replay, policy CRUD |
| 11 | [End-to-End Scenario](11-end-to-end-scenario.md) | Full assignment SLA lifecycle walkthrough with alternative paths |
| 12 | [Reference](12-reference.md) | Enum values, known gaps, verification log |

## Reading order

- **New to the project?** Start with [01-overview.md](01-overview.md), then [02-architecture.md](02-architecture.md).
- **Working on a specific flow?** Jump directly to the relevant flow doc (05–10).
- **Debugging?** Check [12-reference.md](12-reference.md) for enums and known gaps.
