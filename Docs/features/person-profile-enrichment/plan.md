# Person Profile Enrichment — Plan

> **Status:** Planned (2026-06-09). Approved design, no code yet.
> Overview: [README.md](./README.md) · Phases: [phases.md](./phases.md) · Decision: [RD-007](../../repo-decisions/RD-007-profile-enrichment-inferred-kind.md)

## Problem

People arrive from FUB as default-stage `Lead`; industry professionals (realtors, mortgage
agents, lenders) are identified **manually, later**, so `person.created`-triggered workflows
act on agents that still look like leads. We want the platform to infer the classification
itself — early, with evidence — and let a human correct it. See [README §Origin](./README.md#origin).

## Goal

On person ingest, asynchronously produce an evidence-backed **profile** with an inferred
classification, store it on `persons`, expose it to workflows, auto-stamp it (confidence-banded),
and provide a human review/override surface. **No writes to FUB** in v1.

## Non-goals (v1)

- No FUB writes (tags/stage). Classification is platform-internal only.
- No change to `agent_followup_enforcement` (first consumer — separate follow-up).
- No paid-vendor / license-registry providers (pipeline accepts them; none ship).
- No `profile.enriched` domain event / trigger yet.
- No automated "call them to ask" path (the `ai_call` step exists; out of scope here).

## Why not an agent loop

The task is a narrow, repeated, reversible yes/no-plus-category over weak signals
(name/phone/email). A **single web-grounded structured LLM call** per person is the right
default: ~5–10× cheaper than a multi-step tool-using loop, lower latency, far fewer failure
modes (looping, context overflow, tool-misuse), and lower hallucination surface. Without an
authoritative registry oracle (dropped for legal reasons), the core leans on web-search + LLM
judgment, which is mildly agentic — but open-ended autonomy buys nothing here. Reserve any
iterative deepening for the ambiguous tail (common names, no clear hit). Cost at 100–500/day
with dedupe/caching is roughly $300–900/mo worst case, much less in practice.

## Architecture

```
person.created / person.state_changed        (existing typed domain-event rail)
        │  DomainEventListener (NEW @Component — auto-discovered via List<DomainEventListener>)
        │  runs on the after-commit thread → MUST only ENQUEUE, never do I/O inline
        ▼
   enrichment job (PENDING)                   persisted; dedupe/cache key = normalized phone+email
        │  async worker (reuses the due/worker pattern)
        ▼
   EnrichmentPipeline                         ordered, pluggable
        │   └─ EnrichmentProvider (port)
        │        └─ WebSearchLlmClassifier    (v1: the only provider)
        │             web-search API + structured LLM output:
        │             reasoning → evidence[{url, verbatimQuote}] → industry
        │                       → isIndustryProfessional → inferredKind → confidence(low|med|high)
        ▼
   persons.profile  (JSONB, column-scoped write — never clobbers person_details)
        │
        ├─ audit log append (enrichment ran / inferred)
        ├─ ProfileResolver merges person.profile.* into RunContext expression scope (read-only)
        └─ confidence-banded auto-stamp of inferredKind
                 high → set inferredKind + isIndustryProfessional
                 medium → leave UNKNOWN, mark needs-review
                 low → UNKNOWN
        ▼
   Admin `persons` UI: review queue → show evidence → human override
                 override sets source=HUMAN + locked=true (sticky vs re-enrichment)
                 + audit log append (human-edited, who/when)
```

### Profile shape (on `persons.profile`, JSONB)

| Field | Type | Notes |
|---|---|---|
| `status` | enum | `PENDING` / `ENRICHED` / `FAILED` / `SKIPPED` |
| `inferredKind` | string | loose string, **not** an enum (e.g. `realtor`, `mortgage_agent`, `lender`); see RD-007 |
| `industry` | string | free-text category from the classifier |
| `isIndustryProfessional` | bool | the field workflows gate on |
| `confidence` | enum | `low` / `medium` / `high` |
| `evidence` | array | `[{ url, verbatimQuote }]` — verbatim quotes required (anti-hallucination) |
| `source` | enum | `AUTO` / `HUMAN` |
| `locked` | bool | true when human-set; re-enrichment must not overwrite |
| `providers` | array | which provider paths contributed |
| `model`, `enrichedAt`, `reviewedBy`, `reviewedAt`, `version` | meta | provenance |

`kind` (the existing FUB-stage-derived column) is **untouched** — see RD-007.

## Integration points (reuse-first)

- **Trigger:** new `DomainEventListener` `@Component` — auto-registered, no dispatcher change.
  Runs after-commit; **enqueue only**. ([InMemoryDomainEventDispatcher](../../../src/main/java/com/fuba/automation_engine/service/event/InMemoryDomainEventDispatcher.java))
- **Worker:** mirror the existing `due_at` + scheduled-worker pattern used for workflow steps.
- **Persistence:** Flyway **V24** (next free) adds `persons.profile` JSONB + the job/queue +
  the audit table. Writes are **column-scoped** — reuse the anti-clobber discipline that already
  protects `person_details`/tags from races ([FubFollowUpBossClient addTag race note](../../../src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java)).
- **Expression scope:** new `ProfileResolver` parallel to
  [PersonSnapshotResolver](../../../src/main/java/com/fuba/automation_engine/service/person/PersonSnapshotResolver.java);
  extend `CAPTURED_FIELDS` + `WorkflowGraphValidator` so `person.profile.*` validates.
- **HTTP/LLM:** reuse the existing outbound HTTP client/adapter pattern
  ([WorkflowHttpClient] / [AiCallServiceHttpClientAdapter](../../../src/main/java/com/fuba/automation_engine/client/aicall/AiCallServiceHttpClientAdapter.java)).
  API keys via env only — no hardcoded secrets (AGENTS.md).
- **UI:** extend `ui/src/modules/persons/{ui,lib,data}` — not greenfield.

## Order of work

See [phases.md](./phases.md). Ship cut line: **Phases 1–3 = read-only detection** (zero side
effects, safe to land and observe verdict quality), then **4–5 = auto-stamp + human review**.

## Risks & mid-flight detection

| Risk | Mitigation | Detection signal |
|---|---|---|
| Slow web/LLM call stalls the commit thread | Listener enqueues only; worker does I/O | commit-thread timing; worker queue depth |
| Profile write clobbers `person_details` / racing FUB upsert | Column-scoped update; never read-modify-write the blob | `person_details` diff after enrichment |
| LLM hallucinates a classification on a real person | Forced verbatim-quote evidence; abstain to `unknown`/`low`; confidence band gates auto-stamp | manual review of medium band; evidence-empty verdicts |
| Re-enrichment overwrites a human correction | `locked=true` on `source=HUMAN`; pipeline skips locked | audit log shows no AUTO write after HUMAN |
| Cost blowup at volume | Dedupe/cache by normalized phone+email, TTL ~90–180d | per-day enrichment count vs distinct persons |
| Enrichment outage freezes downstream | Profile stays `PENDING`; workflows are null-safe and don't depend on it in v1 | `PENDING` age distribution |
| PIPEDA exposure researching named individuals | Minimize stored data to the classification + its evidence; no general dossier; registry scraping excluded | stored-field audit |

## Validation criteria

- New person → `profile.status=PENDING` within the ingest path **without** slowing ingestion.
- A seeded industry professional reaches `status=ENRICHED` with `isIndustryProfessional=true`,
  a confidence band, and ≥1 evidence item with a verbatim quote + URL.
- An enrichment outage leaves `PENDING` and breaks nothing downstream.
- A workflow `branch_on_field` can read `person.profile.isIndustryProfessional`; the graph
  validator accepts `person.profile.*` references.
- A human override in the admin UI flips the classification, sets `source=HUMAN`/`locked`,
  writes an audit row, and survives a subsequent re-enrichment.
- ≥85% test coverage on changed code (AGENTS.md mandatory testing policy).

## Linked decisions

- **New:** [RD-007 — inferred classification is a separate field; HUMAN > AUTO precedence](../../repo-decisions/RD-007-profile-enrichment-inferred-kind.md).
- **Related:** RD-006 (engine-echo exclusion) — relevant when a *future* phase writes back to FUB; v1 does not, so no echo risk yet.
