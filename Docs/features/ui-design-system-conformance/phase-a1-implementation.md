# Phase A1 — Settings foundation lib (2026-06-04)

## Goal
Pure, testable foundation for the Settings page — no UI, no network. The Zod contract for
`GET /admin/settings/config`, the page-facing display model, the response→display projection, and the
section/row metadata that later phases render against.

## What landed
- `modules/settings/lib/settingsSchemas.ts` — wire schema (only the fields the page surfaces) + display
  `SettingsConfig` / `SecretState` types.
- `modules/settings/lib/settingsProjection.ts` — `projectSettingsConfig(response)`.
- `modules/settings/lib/settingsSections.ts` — `SETTINGS_SECTIONS` (4 sections, dotted keys, control kinds,
  `backed` flags, value selectors); copy pulled from a new `uiText.settings` namespace.
- `src/test/settings-projection.test.ts` — 6 cases (projection + metadata invariants).

## Meaningful decisions
- **Show only real data (Decision B).** The display model carries *only* backend-returned fields. Settings the
  design shows but the endpoint doesn't expose (3 of 4 feature flags; managed-webhooks) are not fabricated —
  they're marked `backed: false` in the row metadata so the UI can render "not available yet". A test pins
  this: only `webhook.sources.fub.enabled` is backed; the other three flags carry no reader.
- **Secrets are presence-only.** Projection collapses `Redactable{present,value}` to `{ present }`; a test
  asserts the redaction sentinel `***` never enters the display model.
- **Schema is lenient on unsurfaced fields.** `fubRetry`/`callRules`/extra `webhook.*` are returned by the API
  but not modelled — Zod strips them (test feeds them in and expects no error), so we don't take a dependency
  on shapes we don't render.
- **Selectors live on the row metadata.** Each backed row carries `read` (or `readSecretPresent`) so the UI
  binds values without a key→value switch; a `backed ⇔ hasReader` invariant is test-enforced.
- **`uiText.settings` added now, not in A6.** The section/row copy is needed by `settingsSections`, so the copy
  namespace landed here; A6's wire-in is just `nav.settings` + the route.

## Trade-offs / surprises
- Section metadata sits in `lib/` (not `ui/`) so it's unit-testable before any component exists — a small
  altitude choice that pays off in A5. No surprises; `knip` stays clean because the test (a knip entry) imports
  every cross-file export.

## Validation
- New test: 6/6 pass. Full `npm run check` green — lint, lint:tokens (no hex), knip (no dead code), build,
  365 tests (+6).

## Repo decisions impact
**No new repo decision.** A1 is pure frontend lib. It *encodes* RD-006 (engine-echo safe-by-default): the
RD-006-governed `engine.write.emit-events` flag is modelled `backed: false` so the UI renders "not available
yet" and never fabricates a value that could misrepresent the echo-safety posture. RD-004 (auth) and RD-005
(wordmark) are not touched at this layer.
