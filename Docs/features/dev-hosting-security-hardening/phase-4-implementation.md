# Phase 4 — Documentation finalization

## Status
`DONE` (2026-05-01)

## What I was thinking

Phases 1–3 shipped the actual hardening; Phase 4 is the housekeeping that turns "code is done" into "future devs can find their way." Three concerns:

1. The upstream security checklist still read like a TODO list. It needs to reflect the post-implementation state — what landed, what was deferred and why, and what triggers a re-evaluation.
2. The deployed env-var contract (`JWT_SECRET`, `ADMIN_AUTH_USERNAME`, etc.) had to live somewhere a deployer would actually look. README is that place.
3. The plan's verification snippets had a long-lived bug — `./mvnw -P prod ...` looking like a Maven profile flag, with no Maven profile of that name. Maven ran the tests anyway with a warning; deployers might copy-paste the snippet and stay confused. Fix and document the distinction.

Plus the operator's two new requests:

4. Add a vertical end-to-end lifecycle diagram to `plan.md` so a future reader can see the codepath without reading the implementation.
5. Make that diagram a workflow rule going forward — every feature plan must include one.

## Decisions taken

- **Security checklist becomes a status doc, not a TODO.** Restructured into four sections: (A) Completed, (B) Known issues — accepted, (C) Nice-to-have, (D) Verified safe. Each entry says what shipped, where, and why a deferral is okay. Future readers can answer "what's the security posture of this dev host?" from this single doc.
- **Known-issues entries get `Accepted because` + `Revisit when`.** Without the trigger conditions, a deferral becomes a permanent forget. With them, a future event (second admin user, untrusted upstream) becomes a hand-off.
- **README "Hosted dev environment" section is operator-facing.** Tabulated env-var contract, references `RD-004` and the security checklist for design context. Pre-deploy verification commands inline so they're impossible to miss.
- **Lifecycle diagram lives at the top of the plan's "Approach" area.** Before the deep work-item sections. Anyone reading the plan top-down sees the diagram first and gets oriented.
- **`AGENTS.md` rule made specific.** Not just "include diagrams" — explicitly: vertical, top-down, names the file path **and** the method/handler at each node. Stale lifecycle diagrams are worse than missing ones; the rule says so.

## Surprises

None worth tabulating. The biggest "surprise" was just realizing how many places carried the `-P prod` mistake forward — phase logs did note it as a "Phase 4 plan correction" item, so it landed cleanly when I went looking.

## Validation

- Backend: `./mvnw clean test` → **395 / 0F / 0E / 36 skipped**, `BUILD SUCCESS`. (Unchanged — no code touched.)
- UI: `npm test` → **342 / 342 passing**. (Unchanged.)
- The `plan.md` Mermaid diagrams all render on GitHub (verified via the markdown preview).

No live deploy verification yet — that happens when the operator provisions the hosted dev URL.

## Repo decisions impact

`No` — Phase 4 is doc-only. The new lifecycle-diagram requirement in `AGENTS.md` is a *workflow* rule (governs how features are documented), not an architectural decision about the codebase. The auth contract that *is* repo-wide was already promoted in [`RD-004`](../../repo-decisions/RD-004-admin-auth-uses-jwt-bearer.md) at the end of Phase 2. Nothing new to register here.
