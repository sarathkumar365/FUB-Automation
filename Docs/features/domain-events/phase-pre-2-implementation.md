# Pre-Phase-2 — Lead to Person rename — implementation log

Status: `DONE`

## Goal

Complete the vocabulary substrate before Phase 2 adds durable domain events. The entity tracked from FUB `/v1/people` is now consistently `Person`; `Lead` remains a possible business classification via `person.kind = LEAD`, not the name of the persisted CRM-contact record.

## Meaningful decisions

### Admin API route hard-cut

The app is not deployed, so the admin read surface was renamed directly from `/admin/leads` to `/admin/persons` with no compatibility alias. The response shape follows the new vocabulary too: summary payloads expose `person`, and list/detail rows expose `sourcePersonId`.

### Full SPA vocabulary rename

The UI rename covers routes, ports, HTTP adapter, schemas, query keys, shared types, page components, labels, and tests. This avoids a half-renamed state where `/admin/persons` is backed by `Lead*` UI types and makes Phase 2's domain-event work easier to reason about.

### Stage filtering stays in workflow logic

The old ingestion-time stage filter remains removed. `PersonUpsertService` persists every FUB person webhook it can fetch, maps stage to `PersonKind`, and the production workflow gates on `person.kind = "LEAD"` so non-lead-stage persons are stored but do not trigger the lead follow-up workflow.

### Historical docs stay historical

Phase 0 and Phase 1 implementation logs keep their original `Lead` wording where they describe what was built at that time. Current feature planning docs and current implementation surfaces use `Person`.

## Validation evidence

- `./mvnw -q -DskipTests compile` passed.
- `./mvnw test` passed: 525 tests, 0 failures, 0 errors.
- `./mvnw test -Dtest=ReplayHarnessTest` passed: 5 tests, 0 failures, 0 errors.
- `cd ui && npm test` passed: 71 files, 349 tests.
- `cd ui && npm run test:e2e` passed: persons route shell smoke.
- Stale-reference grep over production Java and UI source returned no matches for old lead routes, DTO/type names, `sourceLeadId`, `lead_details`, or `NormalizedDomain.LEAD`.

## Repo decisions impact

`No` — local feature concern. The rename aligns this feature's substrate with FUB's `/v1/people` terminology but does not create a new repo-wide architectural rule.
