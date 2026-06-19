# UI Architecture Conformance

> **Status:** ✅ **Complete — 13 / 13 findings resolved** (Phases 1–5). The `ui/` module is aligned with
> `ui/AGENTS.md` + RD-011, with the layering CI-enforced. UAC-13 was discovered during Phase 3 and fixed.
> **Goal:** Drive every finding from the [UI Architecture Audit (2026-06-18)](../../audits/ui-architecture-audit-2026-06-18.md)
> to closure so the `ui/` module is fully aligned with the rules in `ui/AGENTS.md`,
> `developer-rules.md`, and `src/shared/ui/README.md`.
> **Gate (every change):** `npm run check` green (lint + lint:tokens + deadcode + build + test); add/adjust
> tests for changed behavior; browser-verify observable UI.

This folder tracks the remediation of the architecture audit. Nothing here changes product behavior — it is
**maintainability / coherence** work. The audit found no correctness defects.

## Files
- **[tracker.md](tracker.md)** — the canonical issue list. One card per finding (UAC-01 … UAC-12) with
  evidence, fix, acceptance criteria, and status. **Source of truth — keep statuses updated here.**
- **[plan.md](plan.md)** — phased execution plan grouping the findings into reviewable batches.
- **implementation-log.md** — appended as each finding is closed (what changed, files, tests, verification).

## Master status board

Severity = maintainability/risk over ~6 months, not runtime breakage (there is none).

| ID | Finding | Severity | Phase | Status |
|----|---------|----------|-------|--------|
| **UAC-01** | No path aliases → 5–6 level `../` import chains (14 files under WorkflowDetailPage) | HIGH | P1 | ☑ Done |
| **UAC-02** | `platform/` imports schemas from `modules/` (8 sites); inconsistent schema ownership | HIGH | P2 | ☑ Done |
| **UAC-03** | No code-splitting — builder + `@dagrejs/dagre` in main bundle | MED | P4 | ☑ Done |
| **UAC-04** | No lint enforcement of module/layer boundaries | MED | P3 | ☑ Done |
| **UAC-05** | Hardcoded query keys bypass `queryKeys` factory (3 files) | MED | P1 | ☑ Done |
| **UAC-06** | `ui/AGENTS.md` module list stale (says 2 modules; there are 11) | MED | P1 | ☑ Done |
| **UAC-07** | Misplaced hook `useWorkflowDetailActions.ts` in `lib/` | LOW | P1 | ☑ Done |
| **UAC-08** | Cross-module type ownership blur (`WorkflowRunSummary`) | LOW | P2 | ☑ Done |
| **UAC-09** | Recipes hardcode `"Yes"/"No"` (bypass `uiText`) | LOW | P1 | ☑ Done |
| **UAC-10** | `.join(' ')` instead of `cn()` (PanelNav, AppRail) | LOW | P1 | ☑ Done |
| **UAC-11** | Test coverage gaps (real gaps: PersonsPage, WorkflowBuilderPage, WorkflowsPage filters) | LOW | P5 | ☑ Done |
| **UAC-12** | `WorkflowsPage.tsx` ~366 LOC — extract column/filter hooks | LOW | P5 | ☑ Done |
| **UAC-13** | `shared/ui/AppRail` imports `@modules/auth` (shared not a leaf) | LOW | P3 | ☑ Done |

**Status legend:** ☐ Open · ◐ In progress · ☑ Done · ⊘ Won't fix (record rationale in tracker.md)

## Repo-decisions to create (gated on owner sign-off)
- **RD — Schema ownership** (drives UAC-02): platform-owned schemas vs module-owned-with-inward-dependency.
- **RD — Module-boundary lint zones** (drives UAC-04): encode the `app → modules → platform` layer graph.

These two are **decisions, not just fixes** — they need a call before the corresponding phase lands. See
tracker.md UAC-02 / UAC-04 for the options.

## Definition of done (whole effort)
1. All 12 findings ☑ Done or ⊘ Won't-fix (with recorded rationale).
2. The two RDs above written and `Accepted` (or explicitly declined).
3. `ui/AGENTS.md` refreshed to describe the real module set + resolved conventions.
4. `npm run check` green; no new dead code (knip); token guard clean.
5. This README's status board shows 12 / 12 and flips to ✅ Complete.
