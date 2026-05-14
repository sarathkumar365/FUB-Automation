# Runbooks

How-to-operate docs: deploy steps, on-call playbooks, environment procedures. Things you read while *doing* an operation, not while designing one.

## What goes here

- Deploy and release procedures
- On-call response steps for known alerts
- Environment setup or rotation guides
- Manual scripts and how to run them safely

## Conventions

- Folder name: kebab-case, describes the operation (`deploy-runbook`, `railway-deploy-bundled-spa`).
- Each runbook should be runnable cold — assume the reader is the on-call engineer at 2am.
- Keep procedures versioned with the system they describe; update the runbook in the same PR that changes the procedure.

## What does not go here

- Hosting *decisions* and trade-offs → [`../hosting-decision/`](../hosting-decision/)
- New feature design → [`../features/`](../features/)
- Bug investigations → [`../bugs/`](../bugs/)
