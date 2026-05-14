# Features

One subfolder per feature, holding its spec, design notes, and any phase plans.

## Conventions
- Folder name: kebab-case feature slug (e.g. `lead-management-platform`, `workflow-engine`).
- Inside each folder: a primary spec/RFC, plus supporting notes, diagrams, or phase docs as needed.
- Repo-wide decisions surfaced by a feature get promoted to [`../repo-decisions/`](../repo-decisions/).

## What goes here
- Feature specs and RFCs
- Phase plans, rollout notes, vertical-slice breakdowns
- Feature-scoped diagrams or design discussion

## What does not go here
- Bug fixes / defect tracking → [`../bugs.md`](../bugs.md)
- Deploy steps, on-call playbooks, manual scripts → [`../runbooks/`](../runbooks/)
- Hardening, legacy removal, migrations → [`../initiatives/`](../initiatives/)
- Cross-feature architecture → [`../engineering-reference/`](../engineering-reference/)
- Repo-wide decisions → [`../repo-decisions/`](../repo-decisions/)
- Investigations / postmortems not tied to a defect → [`../deep-dive/`](../deep-dive/)

## The "feature?" test

Ask: *"Does this add a capability the system didn't have before?"* If no, it probably belongs in `bugs/`, `runbooks/`, or `initiatives/` instead.
