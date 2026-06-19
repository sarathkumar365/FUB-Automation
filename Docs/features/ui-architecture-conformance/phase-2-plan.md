# Phase 2 — Schema ownership (UAC-02 + UAC-08), Option A

Decision: **Option A — platform owns the validation boundary**, implemented the **`z.infer` (DRY) way**:
move each schema file (zod schema **+** its inferred type, single source of truth) from `modules/` into
`platform/`. Recorded as **RD-011**. No product behavior change.
Per review-before-commit: implement → present → wait for OK.

> **Final home (post self-review): `platform/contracts/`**, not `platform/adapters/http/`. The first cut put
> the schemas in `adapters/http/`, which made ports import from an adapter folder (port→adapter, inconsistent
> with every other port). They were relocated to a neutral `platform/contracts/` leaf. RD-011 and
> `implementation-log.md` (Phase 2 parts 1 + 2) are the canonical record; sections below reflect the final
> `contracts/` layout.

---

## Verification (exhaustive — all culprits)

Dependency inversion = **7 import sites / 5 platform files**, from **3 module files**:
- `platform/ports/workflowPort.ts:9`, `workflowRunPort.ts:5`, `settingsPort.ts:1` — **types**
- `platform/adapters/http/httpWorkflowAdapter.ts:1-8`, `httpWorkflowRunAdapter.ts:1-4` — **zod values**
- `platform/adapters/http/httpSettingsAdapter.ts:1,5` — **zod value + `projectSettingsConfig` logic**

Sanctioned exception (NOT touched): `httpJsonClient.ts:2`, `sseWebhookStreamAdapter.ts:5` import the auth token store.

Files to move are self-contained (import only `zod`):
`modules/workflows/lib/workflowSchemas.ts`, `modules/settings/lib/settingsSchemas.ts`,
`modules/settings/lib/settingsProjection.ts` (the projection imports types from `./settingsSchemas`, which
moves with it).

Consumer counts (basename grep, exhaustive):
- **`workflowSchemas`** — 24 importers (22 `import type`, 2 adapters import zod values).
- **`settingsSchemas`** — 9 importers (7 `import type`, settings adapter + `settings-projection.test` import the zod value).
- **`settingsProjection`** — 2 importers (settings adapter + its test).

---

## Approach — move the files, keep `z.infer`

No type duplication, no hand-written `shared/types`. Each schema file stays as-is (schema + `z.infer` type in
one place) and relocates into `platform/`. The inversion is fixed because the files now **live in** platform;
consumers import from the new location. Direction becomes correct: `modules → platform`.

**Home by consumer breadth (two buckets):**
- App-wide contracts (imported by ports + modules + adapters) → **`platform/contracts/`** — a neutral leaf
  everyone can depend on without inverting a layer.
- Adapter-internal schemas (imported only by their adapter) → stay in **`platform/adapters/http/`**.

```
platform/contracts/
  workflowSchemas.ts          ← from modules/workflows/lib/   (zod + z.infer types, unchanged content)
  settingsSchemas.ts          ← from modules/settings/lib/

platform/adapters/http/
  personSchemas.ts            (stays — adapter-internal)
  webhookSchemas.ts           (stays — adapter-internal)
  processedCallSchemas.ts     (stays — adapter-internal)
  settingsProjection.ts       ← from modules/settings/lib/    (transform logic, not a shape;
                                  imports types from ../../contracts/settingsSchemas)
```

**Accepted trade-off:** the *type-source mechanism* isn't uniform across domains — workflow/settings expose
types via `z.infer` from `platform/contracts/*`; person/webhook hand-write theirs in `shared/types`. Both are
platform-owned and depended on inward; not worth hand-duplicating workflow/settings types to "normalize."

---

## Repoint map (mechanical — mostly `@modules/...` → `@platform/contracts/...`)

**Adapters (in `platform/adapters/http/`) → relative `../../contracts/`**
- `httpWorkflowAdapter.ts`, `httpWorkflowRunAdapter.ts` → `../../contracts/workflowSchemas`
- `httpSettingsAdapter.ts` → `../../contracts/settingsSchemas` (schema) + `./settingsProjection` (logic, local)
- `settingsProjection.ts` → `../../contracts/settingsSchemas` (types)

**Ports (in `platform/ports/`) → relative `../contracts/`** (port → contract, **not** port → adapter)
- `workflowPort.ts`, `workflowRunPort.ts` → `../contracts/workflowSchemas` (types)
- `settingsPort.ts` → `../contracts/settingsSchemas` (type)

**Feature modules + tests (type imports) → `@platform/contracts/...`**
- workflow types (workflows/workflow-runs/dashboard + 2 tests) → `@platform/contracts/workflowSchemas`
- settings types (`SettingRow`, `settingsSections`, `SettingsPage`, `settings-hooks.test`, `settings-page.test`)
  → `@platform/contracts/settingsSchemas`
- `settings-projection.test` → `@platform/contracts/settingsSchemas` + `@platform/adapters/http/settingsProjection`

**UAC-08 resolved:** `WorkflowRunSummary` & friends now have one owner (`platform/contracts/workflowSchemas`);
`dashboard`/`workflow-runs` import from there, not from the `workflows` module. The cross-module borrow is gone.

---

## Step sequence (each reviewable; `npm run check` green between)

1. **RD-011** — write `Docs/repo-decisions/RD-011-ui-schema-ownership.md` (Accepted: Option A, z.infer; two
   homes — `platform/contracts/` for app-wide contracts, `platform/adapters/http/` for adapter-internal;
   auth token import noted as the standing exception). Add to the RD index.
2. **Move files** — `git mv` `workflowSchemas.ts` + `settingsSchemas.ts` → `platform/contracts/`;
   `settingsProjection.ts` → `platform/adapters/http/` (history preserved).
3. **Repoint adapters + ports** — adapters → `../../contracts/*`; ports → `../contracts/*`.
4. **Repoint modules + tests** — type importers + projection test (per map above).
5. **Verify deletion is clean** — old module paths gone; `knip` shows no orphaned files/exports.
6. **Docs** — tick UAC-02 + UAC-08 in `tracker.md` / `README.md`; append to `implementation-log.md`.

No new test file is required (DRY — there is no second type definition to keep in sync). Existing adapter +
hook + page tests already exercise these schemas/types and must stay green.

---

## Risks
- **Missed importer / stale path** → exhaustive basename grep done; `tsc -b` fails on any stale import, so
  nothing can silently slip.
- **`git mv` history** → use `git mv` so blame survives.
- **Scope creep** → do NOT split `workflowSchemas` into workflow vs workflow-run files; move as-is.

## Acceptance
- `grep` of `src/platform` shows **zero** `@modules/` imports except the documented auth exception.
- No port imports from `adapters/http`; schemas are platform-owned (contracts/ + adapters/http by breadth).
- `WorkflowRunSummary` et al. have a single owner; no cross-module type borrow remains.
- RD-011 Accepted; `npm run check` green (396 tests).

## Non-goals
- No change to webhook/person/processedCall (already platform-owned).
- Not converting person/webhook to `z.infer` (out of scope; their hand-written `shared/types` split stays).
- Phase 3 (boundary-lint enforcement, UAC-04) consumes this outcome but is separate.
