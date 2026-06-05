# Phase A2 — Settings platform seam (2026-06-04)

## Goal
Wire the real read path: a `SettingsPort` and its HTTP adapter that calls `GET /admin/settings/config` through
the existing central client, registered in the DI container, with a query key for A3.

## What landed
- `platform/ports/settingsPort.ts` — `SettingsPort { getConfig(): Promise<SettingsConfig> }`.
- `platform/adapters/http/httpSettingsAdapter.ts` — `HttpSettingsAdapter`: `httpClient.get(path, schema)`
  then `projectSettingsConfig`.
- `platform/container.ts` — `settingsPort` registered in `appPorts` / `AppPorts`.
- `platform/query/queryKeys.ts` — `queryKeys.settings.config()`.
- `src/test/settings-adapter.test.ts` — adapter reads the right path and returns the projected config.

## Meaningful decisions
- **Port is read-only for now.** `updateConfig` is left as a one-line comment (future write API), not an
  interface method — no unused stub to satisfy.
- **Adapter stays thin.** Zod validation happens inside `HttpJsonClient.get`; the adapter only supplies the
  path + schema and projects. The test uses a fake client that parses the sample with the passed schema, so
  it exercises the real validate→project wiring without a network.

## Validation
Adapter test passes. Full `npm run check` green — lint, lint:tokens, knip, build, 366 tests (+1).

## Repo decisions impact
**No new repo decision.** Consumes **RD-004 (JWT bearer auth)**: the read rides `HttpJsonClient`, which
attaches the `Authorization: Bearer` header for `/admin/**` paths — no parallel auth. RD-005/006 not touched
at this layer.
