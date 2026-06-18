# Phase 2 — Validator

> 2026-06-18 · Status: implemented; trigger suite green; full-suite + commit pending.

## Goal

Teach the save-time validator the `anyOf` shape without changing how flat triggers
validate. `validate()` delegates structural shape to `ParsedTrigger` and runs the existing
per-trigger checks once per entry, so each filter is validated against its own kind.

## What changed

`DomainEventTriggerValidator.validate(trigger)`:

1. `errors = ParsedTrigger.from(trigger).shapeErrors()` — structural shape (on-XOR-anyOf, non-empty array, duplicate kinds, `reactToEngineEvents` placement).
2. Top-level `reactToEngineEvents` must be a boolean (unchanged).
3. For each entry → `validateEntry(on, filter, errors)`.

`validateEntry` is the old body verbatim — known-kind catalog, valid-JSONata, the
`change.*`/`current.*`/`person.*` field-reference checks, and the per-kind rules — now
applied per entry. SPI call-sites (`AutomationWorkflowService`) are unchanged; the engine
SPI stays `Map`-based.

## Accepted parity deviations

- **Neither-shape message changed.** A trigger with no `on` *and* no `anyOf` now errors
  `"trigger must declare one of 'on' or 'anyOf'"` instead of `"trigger.on is required"`.
  More correct now that `anyOf` exists; still rejected. The one affected test was renamed.
- **Non-string `filter` coerces to absent.** `ParsedTrigger.str()` turns a non-string filter
  into `null`, so it's treated as kind-only (accepted) rather than the old
  `"filter must be a non-empty string"` rejection. Untested edge / malformed authoring;
  accepted rather than adding a filter-type check to `shapeErrors`.

## Minor

An `anyOf` entry missing `on` yields two messages (`anyOf[i].on is required` from
`shapeErrors` + `trigger.on is required` from `validateEntry`). Both accurate; left simple.

## Validation

- `DomainEventTriggerValidatorTest`: 13 originals (one renamed) + 8 new anyOf tests = 21.
- 46 trigger tests green; full-suite run pending before commit.

## Repo decisions impact

No — local feature concern (host trigger glue); no boundary moved, no RD.

## Files

- `service/workflow/trigger/DomainEventTriggerValidator.java`
- test `DomainEventTriggerValidatorTest.java`
