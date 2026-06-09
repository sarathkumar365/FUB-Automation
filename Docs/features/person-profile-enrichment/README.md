# Person Profile Enrichment

> **Status:** Planned (2026-06-09). Design approved; no code yet.
> Canonical plan: [plan.md](./plan.md) · Phase tracker: [phases.md](./phases.md) · Decision: [RD-007](../../repo-decisions/RD-007-profile-enrichment-inferred-kind.md)

## One-line

When a person is ingested, asynchronously build a **profile** that infers whether they
are an industry professional (realtor / mortgage agent / lender / etc.), store it on the
person, expose it to workflows, and let a human review and correct the inference.

## Why

People are created in FUB with the default stage `Lead` and reclassified to
`Realtor` / `Real Estate or Mortgage Agent` **manually, minutes-to-hours later**
(today: a teammate Google-searches or calls them, then edits the stage + tags).
Workflows that fire on `person.created` therefore act on people who *look* like leads
but are actually agents — the [Gordon incident](#origin) is the canonical failure: the
`agent_followup_enforcement` flow nudged and nearly reassigned a real-estate agent
because the agent signal did not exist yet at trigger time.

This feature removes that manual identification work and gives the platform a
first-class, evidence-backed classification it can act on automatically — while keeping
a human in the loop to correct it.

## Scope (v1)

In:
- Async enrichment pipeline (pluggable providers; v1 ships **one**: a web-search-grounded LLM classifier).
- A `profile` stored on `persons` carrying an **inferred classification** + confidence + cited evidence.
- `person.profile.*` exposed to the workflow expression scope (read-only for workflows).
- Auto-stamping the inferred classification (confidence-banded) **on our own profile** — no FUB writes.
- A review surface in the admin `persons` module: see the verdict + evidence, override it, lock it.
- An append-only audit log of enrichment runs and human edits.

Out (deliberately deferred):
- **No writes back to FUB** (no tags, no stage changes) — classification is platform-internal.
- No change to `agent_followup_enforcement` gating (that's the first *consumer*, planned separately — see [Next](#next)).
- No paid enrichment vendor or license-registry providers (the pipeline is *built to accept* them; none ship in v1).
- No `profile.enriched` trigger event yet.

## Key decisions

- **Geography-agnostic, registry-free core.** Multi-region from day one; the authoritative
  Ontario registries (RECO, realtor.ca) are **out** — both forbid commercial use, and PIPEDA
  enforcement (OPC, May 2026) makes scraping them for a commercial CRM a live risk. v1 leads
  with a web-search-grounded LLM classifier whose evidence is search snippets, not scraped
  protected directories. (Research: [research.md](./research.md) once compiled.)
- **Mostly-deterministic, lightly-agentic.** A single web-grounded structured LLM call per
  person — not an open agent loop. Iterative deepening is reserved for the ambiguous tail.
  Rationale and cost comparison in [plan.md](./plan.md#why-not-an-agent-loop).
- **Separate `inferredKind`, not overwriting `kind`.** See [RD-007](../../repo-decisions/RD-007-profile-enrichment-inferred-kind.md).
- **`HUMAN > AUTO > UNKNOWN` precedence.** A human override locks the profile against re-enrichment.
- **Profile lives on `persons`** (current state) + an append-only audit log for history.

## Origin

Investigated 2026-06-05/09: run 229 (`agent_followup_enforcement` v5) fired on `person.created`
for "Gordon Bartozzi Agent" while the FUB snapshot still read `stage=Lead`, `tags=[]`. The
agent stage/tags arrived ~6 min later on a re-sync — too late for the trigger. Compounded by
`PersonUpsertService.mapStageToKind` only exact-matching `"agent"`/`"realtor"`, so even the
enriched stage `"Real Estate or Mortgage Agent"` resolves to `UNKNOWN`. This feature is the
durable fix: infer the classification ourselves, early, with evidence.

## Next (post-v1 consumers)

- Gate `agent_followup_enforcement` on the inferred classification — recommended integration
  is to trigger it on a future `profile.enriched` event (with a fallback timeout so an
  enrichment outage cannot freeze follow-ups).
- Additional enrichment providers (vendor enrichment by phone/email; FSRA open-data for
  Ontario mortgage agents — the one clean registry path).
