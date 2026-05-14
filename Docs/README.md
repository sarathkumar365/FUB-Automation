# Docs

Index of everything under `Docs/`. Use this as the entry point when you're looking for something and don't know where it lives.

For code layout and implementation rules, see [../developer-rules.md](../developer-rules.md).

## Folders

| Folder | What it's for |
|---|---|
| [features/](features/) | One subfolder per **new feature** — specs, design notes, phase plans. Net-new behavior only. |
| [bugs.md](bugs.md) | Lightweight tracker (issue # + description + PR + status). GitHub Issues is the source of truth; this file is the at-a-glance index. |
| [runbooks/](runbooks/) | How-to-operate docs — deploys, on-call playbooks, manual scripts. |
| [initiatives/](initiatives/) | Cross-cutting work that isn't a feature or a bug — hardening, legacy removal, migrations, tech-debt sweeps. |
| [repo-decisions/](repo-decisions/) | Repo-wide architectural and process decisions (ADRs). Mandatory read before touching modules covered by an `Accepted` decision. |
| [deep-dive/](deep-dive/) | Long-form documentation of the lead management platform — overview, architecture, per-flow walkthroughs, end-to-end scenario. |
| [engineering-reference/](engineering-reference/) | Evergreen internal reference — migration plans, testing conventions, known issues, tooling cheat-sheets. |
| [audits/](audits/) | Point-in-time reviews of the codebase (stale code, dependency health, drift). Dated snapshots. |
| [product-discovery/](product-discovery/) | Pre-feature research — user pains, ideas, complaints, inspiration. Inputs to feature specs, not specs themselves. |
| [hosting-decision/](hosting-decision/) | Hosting *decisions* and trade-offs, split by environment (`dev/`, `pro-serving-real-users/`). Procedures live in [runbooks/](runbooks/). |
| [FUB Research & Reports/](FUB%20Research%20%26%20Reports/) | Raw research about Follow Up Boss — API samples, payload captures, vendor reports. |
| [images/](images/) | Screenshots and image assets referenced from other docs. |
| [archive/](archive/) | Superseded or historical docs, frozen for reference. |

## Top-level docs

| File | What it covers |
|---|---|
| [ui-figma-reference.md](ui-figma-reference.md) | Pointer to the Figma source of truth for UI work. |
| [ui-style-guide-v1.md](ui-style-guide-v1.md) | UI style guide v1. |

## "Where does this go?" quick guide

- **Designing a new feature** → new folder under [features/](features/)
- **A defect** → open a GitHub Issue, then add a row to [bugs.md](bugs.md). Long investigations go in [deep-dive/](deep-dive/).
- **Deploy steps / on-call procedure / how to run a script** → [runbooks/](runbooks/)
- **Hardening pass, legacy removal, migration, tech-debt sweep** → [initiatives/](initiatives/)
- **Decision that affects the whole repo** → [repo-decisions/](repo-decisions/)
- **Investigation not tied to a specific defect** → [deep-dive/](deep-dive/)
- **Reusable how-to / reference** → [engineering-reference/](engineering-reference/)
- **Codebase health check** → [audits/](audits/) with a dated filename
- **Idea or user pain, not yet a feature** → [product-discovery/](product-discovery/)
- **Hosting / environment trade-offs** → [hosting-decision/](hosting-decision/)
- **Raw FUB API samples or vendor reports** → [FUB Research & Reports/](FUB%20Research%20%26%20Reports/)
- **Screenshot used in a doc** → [images/](images/)
- **Old doc no longer accurate but worth keeping** → [archive/](archive/)

### Quick decision: is this a feature or a bug?

- **Adds capability the system didn't have before** → feature
- **System was supposed to do X, didn't, now we're fixing it** → bug
- **System does X but we want to stop / clean it up** → initiative
- **System works fine, but we need a written procedure** → runbook
