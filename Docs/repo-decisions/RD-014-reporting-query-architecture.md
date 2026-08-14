# RD-014: Reporting is deterministic-first over a thin semantic layer; natural-language querying is deferred, optional, and demand-gated

## Status
**Accepted (2026-06-26)** — direction agreed in a `/consult` architecture deliberation. Implementation
is phased/incremental (see [reporting-platform/phases.md](../features/reporting-platform/phases.md)).
**Both reports built 2026-08-13/14** on this architecture; see the 2026-08-14 amendment below for the
six decisions that came out of building them.
**Sharpens, does not replace, [RD-012](./RD-012-reporting-platform-architecture.md).** Scope: the
reporting *query / consumption* architecture — how reports are produced and served, and where (if
ever) an LLM / natural-language interface fits.

## Amendment (2026-07-02) — timeline substrate; NL still deferred
Two facts discovered after acceptance sharpen (do not reverse) this decision:
1. **The semantic layer's substrate is an event-sourced per-lead timeline.** The `events` diary already
   captures assignment changes, `contacted` flips, and calls — all timestamped — so the thin semantic
   layer is implemented as **on-read SQL views over `events`**, the keystone being a **holder-intervals**
   view (who held each lead over what interval). Reports join on top. The "person timeline," previously
   deferred, is now the **foundation** of Phase 2, not a future feature. This is still "SQL views, not
   dbt/Cube," still deterministic, still **views over materialized** (decision 6 reinforced).
2. ~~**The accuracy gate is demoted from prerequisite to optional.**~~ **REVERSED 2026-07-02 (audit) —
   the reconcile/backfill job is MANDATORY.** A live FUB-API audit disproved "hosted ⇒ complete": recent
   call capture is ~41%, whole days inside the window carry zero webhooks, and the accountability
   red-list is ~40% false. **Root cause = an ephemeral Cloudflare quick-tunnel webhook ingress + FUB's
   at-most-once delivery (no replay) + no backfill** — not app uptime, not ingestion. Continuous app
   hosting does not make forward windows complete. The 24/7 **and** reconcile prerequisite in
   Consequences below **stands**; only the "reconcile is optional" wording is retracted. Fix = a stable
   public webhook URL **plus** (optionally) a reconcile. See
   [data-truths §2.1 CORRECTION](../features/reporting-platform/findings/data-truths.md) and
   [phases.md](../features/reporting-platform/phases.md) Phase 2c.
   **RESOLUTION 2026-07-02:** the **ingress half is FIXED** — the app runs on Railway at a stable public
   URL with FUB webhooks registered to it (verified: steady hourly ingestion). Forward data is now
   reliable, so **R2 is trustworthy for windows entirely after the fix.** The **reconcile/backfill job is
   PARKED (not built)** — we accept forward-only R2 and do not recover pre-fix history. So the net
   prerequisite for R2 was a *stable ingress*, now met; the reconcile job is optional insurance, deferred.

Deterministic-first, accountability-frozen-in-SQL, and NL-deferred-and-demand-gated all stand unchanged.

## Amendment (2026-08-14) — how reports are computed, and what a contact is

A `/consult` replan revisited the substrate from the root and then built both reports. Six
decisions came out of it. All of them sharpen this RD; none reverse it.

### 1. On-read computation stays. Snapshots and write-time stamping were rejected.
Four ways to answer "who held this lead when the call happened" were rated:

| | Approach | Verdict |
|---|---|---|
| **1** | **On-read SQL view** (chosen) | Cannot drift — recomputed from source every time. Works on all history. Worst case is *slow*, which is visible |
| 2 | Stamp the holder onto each call at write time | Rejected: forward-only, and the only option that **cannot be recomputed** if the rule turns out wrong |
| 3 | A stored ownership-history table | Deferred: same answers, rebuildable from `events`. **This is the upgrade path** if reads ever get slow — an internal swap, invisible to the reports |
| 4 | Ignore reassignment, credit the current holder | Rejected: wrong on 37 of 1,034 calls (3.6%), and wrong precisely on the reassigned leads people dispute |

The deciding distinction: **1 and 3 are derived and always rebuildable; 2 is captured and
permanently wrong if captured wrong.** Measured cost at 5× the brokerage's entire lead count
(100k leads / 300k calls / 400 days): R1 ~199ms, R2 ~96ms. Not a constraint.

A **nightly snapshot table** was also designed and dropped. Its one unique benefit was dating
FUB's `contacted` flag — and decision 2 below removed the need. What remained was a scheduled
job to babysit for an 11% fuzzy signal, on a system whose ingestion has silently stopped twice.

### 2. A contact is a conversation. FUB's `contacted` flag is demoted to "Attempted".
The flag flips on *any* touch. Measured: of 532 flagged leads, only **318 had a real
conversation**; **143 had nothing but failed dials**. A 27% overstatement on the number a
manager acts on. Contact now means an outbound call over the configured threshold, **made by
whoever held the lead at that moment**. Three states: **Spoke / Attempted / Nothing**.

Only 31% of outbound calls clear the threshold (791 of 2,534), so "called" and "spoke to"
differ threefold — this is not a rounding difference.

The classifier is **reused, not re-invented**: the same duration ladder the workflow engine
already applies, with the threshold passed from config as a bind parameter so SQL and engine
cannot drift. It is applied **on read, never stored** — storing a label would freeze the
then-current threshold into history and make old and new rows incomparable the moment it is tuned.

### 3. Windows are calendar days in the business timezone.
Yesterday / Today / This week, resolved against `automation.business-hours.timezone`. Not UTC
(a UTC "day" for a Toronto brokerage starts at 8pm the evening before) and **not rolling** — a
rolling 24-hour window answers a different question every hour, so two people reading the same
board disagree about a settled fact. Month and year need no new code, only wider bounds.

### 4. Company data does not belong in the schema.
A `V25` view encoding this brokerage's ad channels was built and **reverted**. Those values are
one company's marketing channels, not a property of the database: another company's are
different, and adding a channel should not need a migration and a deploy. The mapping now sits
behind the `LeadSourceResolver` interface (`StaticLeadSourceResolver` today), swappable for a
per-company table by adding one `@Primary` bean. Unrecognised values become `Other` rather than
vanishing, so a new channel can never silently drop out of a total.

### 5. The two reports answer different questions and are not required to agree.
**R1 is coverage** — leads counted once against their current holder, so totals reconcile to
arrivals; the agent column is routing, not a scorecard. **R2 is attribution** — each lead
credited to whoever acted on it, falling back to the current holder when nobody did. A
reassigned lead legitimately appears differently in each. An earlier criterion demanding they
match was retracted: enforcing it would have forced double-counting in R1.

### 6. One join and one aggregate per report.
Measured at 20k leads: the per-lead-subquery shape took 112ms where join-and-aggregate took
13ms, because the former re-scans the holder view once per lead. The view cannot push a filter
into itself, so cost tracks total lead count rather than window size — affordable only if each
report scans it once.

## Context
The reporting platform is meant to grow from fixed dashboards toward answering arbitrary questions.
The owner asked: should we build a full natural-language→SQL AI system that answers questions "with
proof," and how reliable/accurate is that? This RD records the deliberation and the decision.

Two bodies of evidence shaped it:

1. **A live data audit (2026-06-25/26)** proving the reporting bottleneck is **data, not the query
   interface** — and that several business truths required to read the data correctly are **not
   present in the schema** for any model to discover. Consolidated in
   [findings/data-truths.md](../features/reporting-platform/findings/data-truths.md).
2. **Current (2025-26) text-to-SQL reliability research.**

### Reliability evidence (text-to-SQL, 2025-26)
- On realistic messy schemas ([BIRD](https://beehive-advisors.com/blog/bird-bench)) SOTA execution
  accuracy is ~72-82%, and a [2026 VLDB analysis](https://www.vldb.org/cidrdb/papers/2026/p5-jin.pdf)
  found that pass/fail metric agrees with human experts only ~62% of the time.
- On real enterprise warehouses, naive NL→SQL accuracy
  [collapses to 10-20%](https://medium.com/@visrow/the-text-to-sql-performance-cliff-2026-why-natural-language-to-sql-breaks-a7281a23dbea)
  vs 85%+ on clean academic sets. The dominant failure mode is **silent** — the query runs and
  returns wrong data.
- Probabilistic models give **non-deterministic** SQL: the same question can yield different numbers
  across runs — indefensible for accountability figures a person contests.
- Production systems that reach 70-90% do so by exposing only
  [5-10 curated views](https://research.aimultiple.com/text-to-sql/) (a semantic layer), never raw
  tables — Snowflake Cortex Analyst, Looker/Gemini, Databricks Genie are **all grounded in an authored
  semantic layer**.
- 2026 enterprise consensus: the semantic layer is **more** important in the AI era, because
  ["AI failures are more often semantic failures than hallucinations"](https://almcorp.com/news/semantic-data-layers-enterprise-ai-2026/)
  (wrong table / wrong grain / wrong aggregation), and "AI can't reason if the business hasn't agreed
  what its data means."
- What **is** genuinely changing: [agentic text-to-SQL](https://arxiv.org/pdf/2502.00675) (schema-probing
  + execute-check-self-correct loops) and [MCP](https://builder.ai2sql.io/blog/mcp-database-model-context-protocol)
  (standardized AI↔DB access) are dissolving the *technical plumbing* of NL querying. They do **not**
  dissolve the need to encode business truths the schema doesn't contain.

## Decision

**1. Three layers: clean data → thin semantic layer → consumption.** Industry-standard shape,
right-sized to a single Postgres:
- **Clean data** — existing mirror tables, made trustworthy by ingestion reliability (RD-012's
  webhooks + reconcile). See the accuracy gate in data-truths.md.
- **Semantic layer** — a *thin* layer encoding the meaning the schema doesn't carry: metric
  definitions (`called`/`contacted`/`connected`/`assigned`/`lead-initiated`/`red`) and data-quality
  truths (owner-pool, uptime-gate, source normalization, `'0'` sentinel, broken `kind`). Implemented
  as **a handful of SQL views + the prose spec in data-truths.md** — **not** dbt/Cube/LookML
  (enterprise machinery, unjustified at this scale). This is the irreducible part AI cannot do for
  us, because the meaning isn't in the database.
- **Consumption** — deterministic reports now; an optional NL interface later.

**2. Deterministic-first — fixed, filterable reports are the product.** Every anticipated question is
a reviewed deterministic report over the semantic layer, with parameters/filters (window, agent,
source, status). A filterable dashboard absorbs the large majority of apparently "ad-hoc" questions
with zero AI. Numbers are exact, reproducible, auditable.

**3. Accountability numbers are frozen in reviewed SQL — never produced by a probabilistic agent.**
The numbers a person is held to (assigned→called, red lists) must be deterministic and defensible.
This extends architecture-direction.md's rejection of live-agent reporting to NL→SQL as well.

**4. Natural-language querying is DEFERRED, OPTIONAL, DEMAND-GATED — not a foregone build.** An
NL/agent interface earns its place only for genuinely *unanticipated* questions, and only if real
demand appears. Build it when — and only when — all three hold:
   (a) users repeatedly ask questions the dashboards + filters can't express;
   (b) the questions are varied/unpredictable (not "one more filter");
   (c) the audience actually types questions (owners/admins; agents rarely do).
   If those don't appear, NL is never built — a valid outcome.

**5. If/when NL is built, it is "agent + MCP over the curated views + the data-truths context"** —
not a bespoke pipeline. Scoped **read-only to the semantic-layer views**, labelled **exploratory**
(never the source of an accountability number), consuming data-truths.md as its business context. The
data-truths doc being written now *is* that future context.

**6. Views over materialized tables until scale demands otherwise.** At current volume (~1.3k
persons, ~4k calls) live SQL views are sub-millisecond and always current; materialization adds
staleness + a refresh job for no benefit. Materialize a specific report only when it is measurably slow.

**7. Use AI at build time, not (yet) run time.** Let the LLM *author* view/metric SQL once, reviewed
by a human before it ships. AI leverage without runtime non-determinism.

## Relationship to RD-012
- The **semantic layer** is RD-012's `definitions` module + the read side of the `ReportingQuery`
  port, made concrete as **SQL views** (the deterministic-SQL adapter) first.
- The deferred **`NlQueryProvider`** is exactly decisions 4/5 here — now explicitly demand-gated.
- **Framework extraction** (providers/registry/generic controller) stays Phase 3, rule-of-three,
  unchanged. The semantic-layer *views* are plain SQL, not the framework, so building them now does
  not violate "extract the framework last."

## Audience & tenancy
- Audience: developer / admin / owner now; agents (with per-agent row scoping) later. Per-agent
  scoping is **not** v1.
- **Single-tenant** (one brokerage). Multi-tenant is out of scope; revisit before any framework
  extraction if it ever changes (it reshapes the port + row security).

## Consequences
- The near-term deliverable is deterministic, filterable reports over thin views — value now, no AI risk.
- The semantic layer + data-truths doc is the keystone: it makes deterministic reports trivial **and**
  is the precondition (and context) for any future NL interface.
- NL may never be built; if it is, it's cheap (agent + MCP over views) and clearly exploratory.
- Accountability stays deterministic and defensible.
- **Ingestion reliability (stable webhook ingress + 24/7 + reconcile) is the real prerequisite for
  call-derived accuracy** — re-affirmed by the 2026-07-02 audit. (The intervening "continuous hosting
  makes windows complete; reconcile optional" claim was DISPROVEN the same day: the ingress is an
  ephemeral quick tunnel and FUB delivery is at-most-once, so ~59% of recent calls are dropped with no
  replay. The **reconcile/backfill job is mandatory.**) See data-truths.md §2.1 CORRECTION.
