# UI Architecture Conformance — Phased Plan

Execution plan grouping the 12 findings in [tracker.md](tracker.md) into reviewable batches. Each phase is
independently reviewable, ends with `npm run check` green, and updates the status board in
[README.md](README.md) + the relevant tracker cards.

**Ordering principle:** unblock/clarify first (aliases, docs), then the decision-gated structural work
(schema ownership → boundary lint), then optional performance + hygiene.

---

## Phase 1 — Low-risk hygiene + unblockers
**Findings:** UAC-01 (aliases), UAC-05 (query keys), UAC-06 (AGENTS.md), UAC-07 (misplaced hook),
UAC-09 (Yes/No), UAC-10 (cn()).

- These are mechanical, isolated, and don't depend on any decision.
- UAC-01 (aliases) lands first as its own commit so later phases use clean imports.
- UAC-06 (AGENTS.md) is updated again at the end of Phase 3 once conventions are locked — this pass just
  fixes the stale module list.
- **Done signal:** all six ☑; no imports with 4+ `../`; no hand-written query keys; no React under `lib/`;
  `npm run check` green.

## Phase 2 — Schema ownership (decision-gated)
**Findings:** UAC-02 (schema inversion), UAC-08 (run type ownership).

- **Blocked on:** RD — Schema ownership (Option A platform-owned vs Option B module-owned). Write + Accept
  the RD before touching code.
- Apply the chosen convention uniformly across workflow / workflow-run / settings AND webhooks / persons so
  there is one rule. Fold UAC-08 (consolidate run/summary types) into the same move.
- **Done signal:** one schema-ownership convention repo-wide; only the documented auth exception crosses
  `platform → modules`; RD Accepted; `npm run check` green.

## Phase 3 — Boundary enforcement (decision-gated, depends on Phase 2)
**Findings:** UAC-04 (lint zones), UAC-06 (AGENTS.md final refresh).

- **Blocked on:** RD — Module-boundary lint zones, and Phase 2 (the platform↔modules rule must reflect the
  chosen schema convention).
- Add `no-restricted-imports` / `no-restricted-paths` zones; add a smoke check proving a wrong import fails
  lint. Finalize AGENTS.md to match enforced reality.
- **Done signal:** lint rejects a deliberate cross-layer violation; AGENTS.md matches; `npm run check` green.

## Phase 4 — Performance (optional, confirm first)
**Findings:** UAC-03 (lazy-load builder).

- Confirm view-only load-time is a real concern before doing this.
- `React.lazy()` builder routes behind `Suspense`; verify dagre leaves the initial chunk.
- **Done signal:** builder/dagre absent from initial bundle; lazy route test; `npm run check` green.

## Phase 5 — Test backfill + decomposition
**Findings:** UAC-11 (coverage gaps), UAC-12 (WorkflowsPage size).

- Backfill behavior tests for run-detail / persons / processed-calls / storyboard interactions.
- Extract `useWorkflowsTableColumns` + filter-state hook from `WorkflowsPage`.
- **Done signal:** named pages each have a behavior test; WorkflowsPage materially smaller, behavior
  unchanged; `npm run check` green.

---

## Cross-phase rules
- **Per AGENTS.md test policy:** every behavior change adds/adjusts a test, runs it, and runs the full suite;
  lint + build must pass each cycle. Browser-verify observable UI via the preview.
- **No dead code:** knip must stay green (UAC-01 may orphan nothing, but moves in P2/P5 can — clean up).
- **Token discipline:** UAC-09/UAC-10 touch shared/ui — keep `lint:tokens` green.
- After each phase: tick the README status board + tracker cards, append to `implementation-log.md`.

## Non-goals
See [tracker.md](tracker.md) "Out of scope". No product-behavior changes anywhere in this effort.

## Risks
- **UAC-01 large diff** — isolate as one commit; lean on `tsc` + tests.
- **Phase 2/3 decision stalls** — if RDs aren't decided, Phases 1, 4, 5 still proceed independently.
- **Schema move (P2) regressions** — adapters validate at the boundary; keep Zod parse sites identical, only
  relocate definitions.
