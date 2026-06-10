# Person Profile Enrichment — Phases

> Tracker for [plan.md](./plan.md). Each phase is independently reviewable and commits
> directly to the feature branch (no phase sub-branches, per AGENTS.md).

Status legend: ⬜ not started · 🟡 in progress · ✅ done

| Phase | Status |
|---|---|
| 1 — Schema, profile store, enqueue-on-event | ⬜ |
| 2 — Provider pipeline + web-search LLM classifier | ⬜ |
| 3 — Expose `person.profile.*` to workflows | ⬜ |
| 4 — Auto-stamp inferred classification (banded) | ⬜ |
| 5 — Human review + edit (admin UI) | ⬜ |

**Ship cut line:** 1–3 = read-only detection (no side effects). 4–5 = auto-stamp + oversight.

---

## Phase 1 — Schema, profile store, enqueue-on-event

**Scope**
- Flyway **V24**: add `persons.profile` JSONB (nullable); enrichment job/queue mechanism
  (status + `due_at` or equivalent); append-only audit table.
- `PersonProfile` model + entity mapping; repository method that updates **only** the
  `profile` column (no read-modify-write of `person_details`).
- New `DomainEventListener` `@Component` reacting to `person.created` (and
  `person.state_changed` when name/phone/email change) that **enqueues** a job and stamps
  `profile.status=PENDING`. No provider runs yet.

**Done signal**
- Migration applies cleanly.
- A new person yields `profile.status=PENDING` and an enqueued job; ingestion latency unchanged.
- Listener does no network I/O (verified — enqueue only).
- Tests green, ≥85% on changed code.

**Out:** any actual classification.

---

## Phase 2 — Provider pipeline + web-search LLM classifier

**Scope**
- `EnrichmentProvider` port + `EnrichmentPipeline` (ordered, merges contributions).
- `WebSearchLlmClassifier` provider: web-search API + structured LLM call.
  Output schema ordered `reasoning → evidence[{url, verbatimQuote}] → industry →
  isIndustryProfessional → inferredKind → confidence`. Abstains to `unknown`/`low`.
- Async worker consumes the queue, runs the pipeline, writes `ENRICHED`/`FAILED`.
- Dedupe/cache by normalized phone+email + TTL. Behind a config flag; API keys via env.

**Done signal**
- Seeded industry professional → `ENRICHED`, `isIndustryProfessional=true`, a confidence
  band, ≥1 verbatim-quoted evidence item with URL.
- Provider failure/outage → `FAILED`/`PENDING`, nothing downstream breaks.
- Re-running the same person hits cache (no duplicate research).
- Tests green (including a stubbed provider), ≥85% on changed code.

**Out:** exposing to workflows; any auto-stamp of `inferredKind`.

---

## Phase 3 — Expose `person.profile.*` to workflows

**Scope**
- `ProfileResolver` merges the `profile` into the RunContext expression scope under
  `person.profile`.
- Extend `CAPTURED_FIELDS` + `WorkflowGraphValidator` so `person.profile.*` references
  validate (e.g. `person.profile.isIndustryProfessional`).

**Done signal**
- A test workflow `branch_on_field` reads `person.profile.isIndustryProfessional` and
  branches; validator accepts the reference; missing profile resolves null-safe.
- Tests green, ≥85% on changed code.

**Out:** any workflow actually gating on it in production (that's the post-v1 consumer).

---

## Phase 4 — Auto-stamp inferred classification (confidence-banded)

**Scope**
- After enrichment, write the verdict onto the profile by band:
  **high** → set `inferredKind` + `isIndustryProfessional`; **medium** → leave `UNKNOWN`,
  flag needs-review; **low** → `UNKNOWN`.
- All purely internal column writes (no FUB). Each write appends an audit row.
- Thresholds configurable.

**Done signal**
- High-confidence verdict stamps `inferredKind`; medium leaves it unset + flagged; low is unset.
- Audit log records each stamp with band + source=AUTO.
- Tests green, ≥85% on changed code.

**Out:** FUB writes; human override.

---

## Phase 5 — Human review + edit (admin UI)

**Scope**
- Extend `ui/src/modules/persons`: review queue (default-filtered to needs-review band),
  verdict display **with evidence** (quotes + URLs), inline override of
  `inferredKind`/`isIndustryProfessional` + approve/reject.
- Override sets `source=HUMAN` + `locked=true`; re-enrichment skips locked profiles.
- Backend endpoint for the override; audit row with who/when.

**Done signal**
- A human flips a verdict; profile shows `source=HUMAN`, `locked=true`; a subsequent
  re-enrichment does **not** overwrite it; audit log shows the human edit.
- UI + backend tests green; UI test policy per AGENTS.md satisfied.

**Out:** FUB writes; gating production workflows.
