# Initiatives

Cross-cutting work that isn't a new feature and isn't a bug fix — security hardening passes, legacy-code removal, migration efforts, tech-debt sweeps.

## What goes here

- Security hardening campaigns
- Removing or disabling legacy behavior
- Library / framework migrations that span multiple features
- Coordinated cleanups (dead code, dependency upgrades)

## Conventions

- Folder name: kebab-case, names the initiative (`dev-hosting-security-hardening`, `disable-hardcoded-task-creation`).
- Each initiative folder should have a `plan.md` (what + why) and `research.md` or phase docs as needed.
- When an initiative produces a repo-wide rule, promote that rule to [`../repo-decisions/`](../repo-decisions/).

## What does not go here

- A single new capability → [`../features/`](../features/)
- A specific defect → [`../bugs/`](../bugs/)
- Migration *plans* tied to one library (e.g. Jackson upgrade reference) → [`../engineering-reference/`](../engineering-reference/)
