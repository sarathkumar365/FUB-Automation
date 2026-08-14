# Features

One subfolder per feature, holding its spec, design notes, and any phase plans.

## Conventions
- Folder name: kebab-case feature slug (e.g. `lead-management-platform`, `workflow-engine`).
- **Docs move through two states** (full rules in [`../../AGENTS.md`](../../AGENTS.md) → *Feature documentation workflow*). The tell: an `implementation-log.md` means archived; without one, active.
  - **Active (in development)** — the granular working set: `research.md`, `plan.md`, `phases.md`, and one `phase-<n>-implementation.md` per phase.
  - **Archived (all phases done)** — consolidated to three files: `README.md` (entry point + tracker), `plan.md` (design + research), `implementation-log.md` (append-only, one dated section per phase).
  - **Trigger:** when `phases.md` shows every phase complete, consolidate to the archived shape — a mandatory definition-of-done step. Reopening later appends a new section to `implementation-log.md`; it does not revert to granular.
  - A trivial feature (≤1 phase) may start and stay as a single `README.md`.
- Repo-wide decisions surfaced by a feature get promoted to [`../repo-decisions/`](../repo-decisions/).

## What goes here
- The feature's design, current state, and implementation history (the files above).
- Feature-scoped diagrams or design discussion.

## What does not go here
- Bug fixes / defect tracking → [`../bugs.md`](../bugs.md)
- Deploy steps, on-call playbooks, manual scripts → [`../runbooks/`](../runbooks/)
- Hardening, legacy removal, migrations → [`../initiatives/`](../initiatives/)
- Cross-feature architecture → [`../engineering-reference/`](../engineering-reference/)
- Repo-wide decisions → [`../repo-decisions/`](../repo-decisions/)
- Investigations / postmortems not tied to a defect → [`../deep-dive/`](../deep-dive/)

## The "feature?" test

Ask: *"Does this add a capability the system didn't have before?"* If no, it probably belongs in `bugs/`, `runbooks/`, or `initiatives/` instead.
