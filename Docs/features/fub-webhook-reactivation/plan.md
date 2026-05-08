# FUB Webhook Reactivation — Plan

## Goal
Add a manual script that checks managed FUB webhooks and re-enables only those in `Disabled` status.

## Scope
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

## Execution Notes
- Keep changes isolated from unrelated in-progress files.
- Do not log secrets.
- Keep script behavior deterministic and shell-safe (`set -euo pipefail`).
