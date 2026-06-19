# RD-011: UI API schemas are platform-owned (single `z.infer` source)

## Status
Accepted (2026-06-18). Implementation = **ui-architecture-conformance Phase 2** — see
[`plan.md`](../features/ui-architecture-conformance/plan.md) "Phase 2" (findings UAC-02 + UAC-08).
Scope: `ui/` submodule only.

## Context
The `ui/` hexagonal layering is `modules → platform` (platform is the inner contract/transport layer that
owns API adapters + Zod validation). The architecture audit (2026-06-18) found this inverted for three
domains: `platform/` ports and adapters imported Zod schemas/types — and `projectSettingsConfig` business
logic — **up** from feature modules (`modules/workflows/lib/workflowSchemas.ts`,
`modules/settings/lib/settingsSchemas.ts`, `settingsProjection.ts`), at 7 import sites across 5 platform files.

It was also **internally inconsistent**: webhook / person / processed-call schemas already live correctly in
`platform/adapters/http/*Schemas.ts`, while workflow / workflow-run / settings schemas lived in modules. Two
conventions for the same job (UAC-02). A related ownership blur (UAC-08): `WorkflowRunSummary` and other
run types lived in the `workflows` module and were borrowed cross-module by `dashboard` and `workflow-runs`.

The pre-existing "correct" domains use two different type mechanisms: person/webhook **hand-write** domain
types in `shared/types/*` and keep Zod separately in `platform/adapters/http/*Schemas.ts` (deliberate
type↔schema duplication); processed-calls defines types inline in its port. Workflow/settings used `z.infer`
(schema and type in one file, DRY).

## Decision
**Platform owns the API validation boundary.** Move the schema files from `modules/` into platform, keeping
the **`z.infer` (single-source) style** — schema and its inferred type stay in one file.

**Two homes, by consumer breadth:**
- **`platform/contracts/`** — schemas that are **app-wide contracts** (imported by ports, feature modules,
  *and* adapters): `workflowSchemas.ts`, `settingsSchemas.ts`. A neutral home everyone can depend on without
  inverting any layer.
- **`platform/adapters/http/`** — schemas that are **adapter-internal** (imported only by their adapter;
  their types live in `shared/types`): the existing `personSchemas.ts`, `webhookSchemas.ts`,
  `processedCallSchemas.ts`. These stay put.

Settings *projection* (`projectSettingsConfig`) is transport/transform **logic**, not a shape, so it stays in
`platform/adapters/http/settingsProjection.ts` and imports its types from `platform/contracts/settingsSchemas`.

Imports:
- adapters import schemas from `../../contracts/<domain>Schemas` (adapter → contract, correct direction);
- ports import types from `../contracts/<domain>Schemas` (port → contract, **not** port → adapter);
- feature modules import types from `@platform/contracts/<domain>Schemas`.

Dependency direction is restored (`modules → platform`) and no port depends on an adapter.

### Why `contracts/` and not `adapters/http/` for these two
The existing `adapters/http/*Schemas.ts` are imported **only by their adapters** — they are implementation
detail. `workflow`/`settings` schemas are imported across ports + modules + adapters, so placing them in
`adapters/http` would force ports and modules to depend on an adapter folder (a backwards dependency in a
ports-and-adapters layer, and inconsistent with how every other port sources its types). A neutral
`contracts/` home avoids that.

**`z.infer` over the hand-written split (owner-decided 2026-06-18).** We keep one source of truth rather
than duplicating each shape into `shared/types`. Accepted trade-off: the *type-source mechanism* is not
uniform across domains — workflow/settings expose types via `z.infer` from `platform/contracts/*`, while
person/webhook hand-write types in `shared/types`. Both are platform-owned and depended on inward; judged not
worth hand-duplicating workflow/settings types to "normalize."

**Standing exception (unchanged):** `httpJsonClient.ts` and `sseWebhookStreamAdapter.ts` may import the auth
token store from `modules/auth/state` — a legitimate cross-cutting transport concern, not a layering break.

**UAC-08:** run types now have a single owner (`platform/contracts/workflowSchemas.ts`); no cross-module
type borrowing.

## Going-forward rule (applies to all new work)
**New API schemas use the single-source `z.infer` style, placed in `platform/`:**
- `platform/contracts/<domain>Schemas.ts` — Zod schema **+** its `z.infer` types in one file, when the shape
  is consumed beyond its adapter (ports / modules / other adapters).
- `platform/adapters/http/<domain>Schemas.ts` — only if the schema is purely adapter-internal.
- Ports and modules import the inferred **types** from the schema file. Never hand-duplicate a type into
  `shared/types` for a new domain.

**Legacy exceptions — leave as-is, do NOT copy for new work, do NOT "tidy" without a new decision:**
- `person`, `webhook` — types hand-written in `shared/types/*`, Zod separately in `platform/adapters/http/*`
  (the older "write-twice" split).
- `processed-calls` — types declared inline in `platform/ports/processedCallsPort.ts`.

These three already satisfy the layering rule (platform-owned, correct direction); their only difference is
the type-writing *style*. Converting them is explicitly **out of scope** — they are marked here so the
divergence does not read as an accident.

## Consequences
- Schemas are platform-owned, placed by consumer breadth: app-wide contracts in `platform/contracts/`,
  adapter-internal schemas in `platform/adapters/http/`. No port depends on an adapter.
- Type-mechanism still varies by domain (`z.infer` for workflow/settings vs hand-written `shared/types` for
  person/webhook) — accepted.
- Enables Phase 3 (UAC-04): a lint rule can ban `platform → modules` imports (except the auth exception) and
  `ports → adapters`.
- Not in scope: converting person/webhook to `z.infer`; splitting `workflowSchemas` into workflow vs
  workflow-run files; relocating person/webhook schemas (they are correctly adapter-internal).
