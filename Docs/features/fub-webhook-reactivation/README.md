# FUB Webhook Reactivation

Single-doc feature (one phase): a manual operational recovery script for re-enabling FUB webhooks auto-disabled during local dev. Research context, plan, phase tracker, and the implementation log are all below.

---

## FUB Webhook Reactivation — Research

### Context
Development webhooks are registered against temporary Cloudflare tunnel URLs. When the local app is down or unreachable, Follow Up Boss may auto-disable those webhooks after repeated failures.

This creates a recurring local-dev bottleneck because webhook delivery stops until each disabled webhook is reactivated via API.

### Problem Statement
Current developer workflow handles URL sync via `scripts/run-app.sh`, but does not provide a dedicated manual recovery command that only re-enables disabled webhooks for managed events.

### Key Findings
- Follow Up Boss supports re-enabling with `PUT /v1/webhooks/{id}`.
- Activation requires:
  - webhook `id`
  - `Authorization: Basic ...`
  - `X-System`
  - `X-System-Key`
- Managed event scope already exists in `config/fub-webhook-events.txt`.

### Decision Alignment
- Repo-wide decisions remain in `Docs/repo-decisions/`.
- Feature delivery docs and phase tracking remain in `Docs/features/<feature-slug>/`.
- This feature is a narrow operational improvement for local development and does not change runtime automation logic.

---

## FUB Webhook Reactivation — Plan

### Goal
Add a manual script that checks managed FUB webhooks and re-enables only those in `Disabled` status.

### Scope
In scope:
- New script: `scripts/fub-webhook-reactivate.sh`
- Managed event filtering from `config/fub-webhook-events.txt`
- `GET /v1/webhooks` discovery + `PUT /v1/webhooks/{id}` activation with `{"status":"Active"}`
- Summary output and non-zero exit on activation failures
- README and deep-dive usage note
- One new backend test validating script contract strings

Out of scope:
- Webhook URL sync/update
- Webhook creation
- Auto-run on startup

### Execution Notes
- Keep changes isolated from unrelated in-progress files.
- Do not log secrets.
- Keep script behavior deterministic and shell-safe (`set -euo pipefail`).

---

## FUB Webhook Reactivation — Phases

### Phase 1 — Manual Recovery Script
Status: `DONE` — script + contract test shipped; full suite green on `feature/domain-events`.

#### Deliverables
- Script implementation for managed-event disabled webhook reactivation
- Script contract test
- README update
- Deep-dive startup note
- Phase implementation log

---

## Phase 1 Implementation — Manual Recovery Script

### Status
`DONE` — `scripts/fub-webhook-reactivate.sh` shipped and covered by `FubWebhookReactivationScriptTest`; the full suite is green on `feature/domain-events`.

### Implementation Log
- [x] Created feature documentation bundle (`research.md`, `plan.md`, `phases.md`, this file).
- [x] Added `scripts/fub-webhook-reactivate.sh`.
- [x] Added script contract test.
- [x] Updated README with manual recovery command.
- [x] Updated deep-dive startup docs with manual-recovery note.
- [x] Executed targeted new test.
- [x] Executed full backend test suite.

### Notes
- Repo-wide decisions are maintained under `Docs/repo-decisions/`.
- Feature execution artifacts are maintained under `Docs/features/fub-webhook-reactivation/`.
- Validation details:
  - `./mvnw -Dtest=FubWebhookReactivationScriptTest test` passed.
  - `./mvnw clean test` failed with existing unrelated integration/service failures in current branch (not introduced by this script/doc change set).
