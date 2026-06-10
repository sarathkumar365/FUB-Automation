# RD-008: Profile enrichment infers a separate classification; HUMAN > AUTO; stored on `persons`

## Status

Accepted (2026-06-09). Implemented by the **person-profile-enrichment** feature
([plan](../features/person-profile-enrichment/plan.md)). No code yet at time of writing.

## Context

People enter from FUB as default stage `Lead` and are reclassified to `Realtor` /
`Real Estate or Mortgage Agent` **manually, minutes-to-hours later**. Workflows triggered on
`person.created` therefore act on industry professionals that still look like leads — see the
Gordon incident ([feature README §Origin](../features/person-profile-enrichment/README.md#origin),
[known-issue #34](../engineering-reference/known-issues.md)). Compounding it,
`PersonUpsertService.mapStageToKind` only exact-matches `"agent"`/`"realtor"`, so the enriched
stage `"Real Estate or Mortgage Agent"` resolves to `kind=UNKNOWN`.

We are adding an AI enrichment pipeline that infers whether a person is an industry
professional, with evidence, early enough to matter. Three shaping questions had to be settled:
(1) where the inferred classification lives relative to the existing `kind`; (2) how human
corrections relate to machine inference; (3) where the profile is persisted.

## Decision

**1. The inferred classification is a separate field, not an overwrite of `kind`.**
The enrichment verdict is stored as `profile.inferredKind` (plus `industry`,
`isIndustryProfessional`), distinct from the FUB-stage-derived `kind` column, which is left
untouched. This preserves full provenance — "FUB stage said X, enrichment inferred Y, a human
set Z" — and keeps the stage-derived `mapStageToKind` logic independent of the AI path.
Workflows choose which to read. (Rejected: overwriting the single `kind` column with a
precedence rule — simpler for authors but destroys the provenance trail and entangles two
unrelated sources.)

**2. `inferredKind` is a loose string, not a locked enum.** The net is broad
("industry-adjacent": realtor, mortgage agent, lender, broker, assistant…). Workflows gate on
the coarse boolean `isIndustryProfessional`; `inferredKind`/`industry` are descriptive strings
so new categories don't require an enum migration each time. (The existing `PersonKind` enum —
LEAD/AGENT/REALTOR/UNKNOWN — stays as-is for the stage-derived `kind`.)

**3. Precedence is `HUMAN > AUTO > UNKNOWN`, enforced by a lock.** A human override sets
`source=HUMAN` and `locked=true`; re-enrichment must skip locked profiles. A human correction
is sticky and authoritative; machine inference never overwrites it.

**4. The profile is stored on `persons` (current state) + an append-only audit log.** The
live verdict is a `persons.profile` JSONB column — which makes it flow into the workflow
expression scope through the existing `PersonSnapshotResolver`/`ProfileResolver` path with
minimal plumbing. Because there are both automated and human writes, an append-only audit log
(enrichment-ran / inferred / human-edited) carries the history a single column can't. (Rejected
for v1: a fully versioned side table — heavier than needed; the audit log covers the
"who/when/what evidence" requirement.)

## Scope guardrails (v1)

- **No writes back to FUB.** "Auto-tag" means stamping our own platform profile with the
  inferred classification — *not* writing FUB tags or stage. FUB write-back (and the
  engine-echo concerns of [RD-006](./RD-006-engine-echo-exclusion-safe-by-default.md)) are out
  of scope until a later phase explicitly takes it on.
- **Registry-free, geography-agnostic core.** The authoritative Ontario registries (RECO,
  realtor.ca) are excluded: both forbid commercial use, and PIPEDA enforcement (OPC, May 2026)
  makes scraping them for a commercial CRM a live legal risk. v1 uses a web-search-grounded LLM
  classifier whose evidence is search snippets, not scraped protected directories. FSRA
  open-data (the one clean registry path) and paid vendor enrichment are deferred providers.
- **Data minimization (PIPEDA).** Store the classification + its cited evidence only — not a
  general dossier on the individual.

## Impact

- **Schema:** Flyway V24 adds `persons.profile` JSONB, the enrichment job/queue, and the audit
  table. Profile writes are column-scoped (never read-modify-write `person_details`).
- **Expression scope:** `person.profile.*` becomes addressable; `CAPTURED_FIELDS` +
  `WorkflowGraphValidator` extended to validate it.
- **Existing `kind`:** unchanged. The known `mapStageToKind` multi-word limitation is *not*
  fixed here — the inferred classification sidesteps it; a separate fix can still address `kind`
  if desired.
- **Future consumer:** gating `agent_followup_enforcement` on `isIndustryProfessional`
  (recommended via a future `profile.enriched` event with a fallback timeout) is the first
  intended use — tracked in the feature README, not this decision.
