# Docs consolidation — 2026-06-03

Point-in-time record of a documentation **structure** pass (tidiness — reduce per-feature file count). No code touched; doc content preserved verbatim, only co-located and re-linked.

## Why

Each feature folder had accumulated many small docs — the AGENTS.md convention mandated a separate `phase-<n>-implementation.md` per phase plus `research`/`plan`/`phases`, so an 8-phase feature became 11+ files, many of them 20–50 line stubs. The pain was raw file count in git, not findability.

## New convention (now in AGENTS.md + Docs/features/README.md)

A feature's docs move through **two states**:

- **Active (in development)** — the granular working set: `research.md`, `plan.md`, `phases.md`, one `phase-<n>-implementation.md` per phase. The better workspace while building.
- **Archived (all phases done)** — consolidated to three files:

| File | Role | Mutability |
|---|---|---|
| `README.md` | Entry point: what it is, current state, phase tracker | living |
| `plan.md` | Approved design, rationale, research, lifecycle diagram | living |
| `implementation-log.md` | One dated `## Phase <n>` section per completed phase | **append-only** |

**Trigger:** when `phases.md` shows every phase complete, consolidate — a mandatory definition-of-done step. Reopening later appends a new section to `implementation-log.md`; it does not revert to granular. The append-only log honours the standing rule that dated implementation logs are historical records — co-located, never rewritten.

The features below were all phase-complete, so this pass moved them to the archived shape in one batch and set the convention going forward.

## What moved (targeted features; `workflow-engine` and `ai-call-java-integration` left as-is)

| Feature | Before | After | Merge map |
|---|---|---|---|
| `domain-events` | 17 | **3** | `README` ← overview + phases + current-wiring · `plan` ← plan + phase-2/3/4-plan + race-matrix · `implementation-log` ← 9 phase logs |
| `lead-management-platform` | 12 | **3** | `README` ← phases · `plan` ← lead-management-platform-plan + plan + research · `implementation-log` ← phase-1…8 |
| `fub-webhook-reactivation` | 4 | **1** | single `README` (research + plan + phases + the one phase log) |
| `agent-followup-enforcement` | 4 | **2** | `README` ← phases · `plan` ← plan + research + field-observations (`workflow.json` kept) |
| `state-change-events` | 1 | **1** | `design.md` → `README.md` |
| `engine-extraction` | 1 | **1** | `plan.md` → `README.md` |

**Net: 39 → 11 markdown files (−28).**

## Verification

- **Content preservation** — merged-file line counts reconcile exactly against the original files in git HEAD (domain-events: log 751, plan 1297, README 940; lead-mgmt log 1236). No content dropped; only heading levels demoted by one and links repointed.
- **Links** — repo-wide remap of every cross-doc reference to a moved/renamed file (deep-dive banners, `known-issues.md`, `RD-006`, `product-discovery/ideas.md`, sibling features, AGENTS.md, memory index). Final scan: **0 dangling `](*.md)` links across all 120 docs.**

## Deferred

- Full content rewrite of `Docs/deep-dive/` (10 docs) — still banner-guarded as historical; a faithful per-flow refresh remains its own pass.
- `workflow-engine/` (40 files, 3 overlapping org schemes) — out of scope this round.
