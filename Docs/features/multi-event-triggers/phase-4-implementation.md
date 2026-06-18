# Phase 4 — Catalog/docs + MVP repoint

> 2026-06-18 · Status: code done, green; live repoint + UI-render check pending (operational).

## Goal

Surface `anyOf` to authors and repoint the MVP workflow to it — the step that actually
closes the Facebook-lead gap the whole feature exists for.

## What changed (code)

- **`AdminWorkflowController.getTriggerTypes()`** — adds an `anyOfShape` entry alongside the
  flat `shape`. The `/admin/workflows/trigger-types` catalog now documents both forms.
- **`create-workflow-json/SKILL.md`** — corrected the skeleton's trigger to `{ on, filter }`
  (the old `{ type, config }` form is gone) and added a **Trigger shapes** section covering
  flat + `anyOf` and the shape rules. The skill's deeper staleness (`FubWebhookTriggerType`,
  `eventDomain`/`eventAction`, known-issue #17) is out of scope here — flagged for a separate
  cleanup.
- **`mvp-wf.workflow.json`** — `agent_followup_enforcement_mvp`'s trigger repointed to:
  ```
  anyOf:
    - on: person.created       filter: person.kind='LEAD' and $boolean(person.assignedUserId)
    - on: person.state_changed filter: person.kind='LEAD' and change.assignedUserId.changed and $boolean(change.assignedUserId.new)
  ```
  `__POND_ID__` left as-is (per owner). This is the committed template; the live workflow is a
  separate POST (see operational remainder).

## Verification

- `mvpAnyOfTriggerIsValid` — the **exact** MVP `anyOf` filters pass the validator (so the
  template won't be rejected on save).
- `AdminWorkflowControllerTest` — `/trigger-types` now returns `anyOfShape`.
- Full suite green.

## Operational remainder (not code — on a running instance)

1. **Repoint the live workflow.** The MVP workflow is not seeded in code; POST the repointed
   JSON as a new version to `/admin/workflows`.
2. **Rita replay.** Replay person 21007 (the Facebook-lead repro) and confirm it now plans a
   run via the `person.created` entry.
3. **Admin UI.** Confirm the workflow detail view renders an `anyOf` trigger (JSON passthrough
   expected — verify, don't assume).

Once those land, consolidate the feature docs to the archived shape (per AGENTS.md).

## Repo decisions impact

No — local feature concern (host trigger glue); no boundary moved, no RD.

## Files

- `controller/AdminWorkflowController.java`, test `AdminWorkflowControllerTest.java`
- `service/workflow/trigger/DomainEventTriggerValidatorTest.java` (MVP-trigger validity test)
- `.claude/skills/create-workflow-json/SKILL.md`
- `Docs/product-discovery/WORKFLOWS/MVP WF/mvp-wf.workflow.json`
