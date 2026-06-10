# Phases — Reporting

Tracker for the active feature. See [`plan.md`](./plan.md) for detail (the reporting-module-first structure,
lifecycle diagrams, RD-impact, file lists), [`research.md`](./research.md) for findings + the resolved scope
decisions D1–D4. **Full-stack: Part A (backend reporting model) lands first (or freezes the DTO contract first);
Part B (dashboard surface) consumes it.** Naming stays report-agnostic so future report types are additive.
Every phase that adds code lands its own test.

## Part A — Backend reporting model

| # | Phase | Status |
|---|-------|--------|
| A1 | Shared read-model + aggregate SQL — hourly series, group-by-status counts, funnel join, p50, window helpers (`ReportingReadRepository` + JDBC impl, reusable by future reports) + test | ⬜ Not started |
| A2 | Dashboard report service + DTOs — `DashboardReportService.build` (24h + prior window → deltas) over the shared read-model + test | ⬜ Not started |
| A3 | Controller — `AdminReportingController` `GET /admin/reporting/dashboard` (RD-004 auth) + web-layer test | ⬜ Not started |
| A4 | **Stub** run-replay — `POST /admin/workflow-runs/{id}/replay` (202 FAILED only, no re-exec, no event emit) + test | ⬜ Not started |

## Part B — Dashboard surface (consumes the reporting module)

| # | Phase | Status |
|---|-------|--------|
| B1 | Reporting data seam + projection — schemas, `reportingPort` + `httpReportingAdapter` + DI + queryKeys, `modules/reporting/data/useDashboardReportQuery`, `modules/reporting/lib/dashboardReport.ts` (conversion %, deltas, headline) + tests | ⬜ Not started |
| B2 | Chart primitives — `AreaChart` + `Sparkbars` (token-driven SVG, reduced-motion) + tests | ⬜ Not started |
| B3 | Hero — kicker, headline + live pill, stat strip w/ deltas, throughput chart + test | ⬜ Not started |
| B4 | Funnel rail — 4 stages + connectors + conversion % + sparkbars + test | ⬜ Not started |
| B5 | Recent Runs (ledger `DataTable`) + Needs attention worklist (call replay real / run replay stub, ConfirmDialog → toast → invalidate) + test | ⬜ Not started |
| B6 | Assemble `DashboardPage` (drop panel/inspector) + copy (`uiText.dashboard`) + wire-in + page test | ⬜ Not started |
| B7 | Gate — `npm run check` + `./mvnw test`, browser-verify (light/dark, real backend, replay recompute) | ⬜ Not started |

**Status legend:** ⬜ Not started · 🟡 In progress · ✅ Complete

When all phases are ✅, consolidate to the archived shape (`README.md` + `plan.md` + `implementation-log.md`) per
`Docs/features/README.md` conventions. Each `phase-<n>-implementation.md` must carry its own "Repo decisions
impact" note.
