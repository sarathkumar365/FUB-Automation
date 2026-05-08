# Workflow Builder UI — MVP Plan

## Context

Wave 1 + 2 of the workflow engine landed (graph storage, step registry, JSONata templating, validator, admin CRUD). The original spec called for a visual React Flow builder (`docs/features/workflow-engine/workflow-engine-implementation-plan.md` §3) but shipped-for-now UI was read-only JSON textareas (`docs/features/workflow-engine/UI/WF Operator UI - plan.md`). This plan picks up the deferred visual builder.

**Goal:** replace JSON-textarea authoring with a hybrid canvas + JSON editor so non-engineers can build workflows, with live server validation and dry-run.

## Decisions

- **Hybrid** authoring: React Flow canvas + always-visible JSON sidebar, two-way sync, canonical Zustand store.
- **Thin MVP**: palette → drag → connect result-code edges → RJSF per-node config → server validate → save draft → publish. No autocomplete / auto-layout in v1.
- **Fix G6 first**: add trigger to create/update DTOs before builder ships, so trigger drawer is clean.
- **Dry-run in scope**: new `POST /admin/workflows/dry-run` using a separate `DryRunWorkflowRunner` (not an executor flag) to isolate from real side-effects.

## Backend changes

### B1. Catalog endpoints (`AdminWorkflowController`)
- `GET /admin/workflows/step-types` → `List<StepTypeCatalogEntry>` with `id`, `displayName`, `configSchema`, `declaredResultCodes`, `dynamicResultCodes` flag. Reuse existing `StepTypeCatalogEntry`; add `displayName` default on `WorkflowStepType`.
- `GET /admin/workflows/trigger-types` → same shape for `TriggerTypeCatalogEntry`.
- Service method in `AutomationWorkflowService` iterates `WorkflowStepRegistry` / `WorkflowTriggerRegistry`.

### B2. G6 trigger DTO fix
- Add `TriggerSpec trigger` to `dto/CreateWorkflowRequest` and `dto/UpdateWorkflowRequest` (new record: `type`, `config`, optional `filters`).
- New `TriggerValidator` invoked from `AutomationWorkflowService.createWorkflow/updateWorkflow`.
- Extend `AutomationWorkflowServiceTest` for create-with-trigger + update-trigger-only.

### B3. Dry-run
New package `service/workflow/dryrun/`:
- `DryRunWorkflowRunner` — mirrors `WorkflowExecutionManager` traversal, no DB writes, uses `DryRunStepContext` with mock `ExpressionScope`.
- `DryRunStepAdapter` interface: `simulate(config, scope) → SimulatedResult { resultCode, outputs, logLines }`.
- Per-step adapters (keyed by `stepType.id()`):
  - Pure-compute steps (`branch_on_field`, `set_variable`) **actually evaluate**.
  - Side-effect steps (`fub_*`, `http_request`, `slack_notify`) log + return canned success.
  - Wait steps return stub outcome (configurable via `dryRunOverrides` in request).
- **Startup guard**: `@PostConstruct` asserts every `WorkflowStepType.id()` has a `DryRunStepAdapter` bean — fail boot otherwise. Prevents drift.
- Controller: `POST /admin/workflows/dry-run`, body `DryRunRequest { graph, trigger, samplePayload, dryRunOverrides? }`, response `DryRunResponse { trace[], finalOutputs, errors[] }`.

## Frontend changes

### Deps to add (`ui/package.json`)
`@xyflow/react@^12`, `@rjsf/core`, `@rjsf/validator-ajv8`, `@rjsf/utils`, `zustand`, `nanoid`.

### Routes (`ui/src/app/router.tsx`)
Add `/admin/workflows/new` and `/admin/workflows/:key/edit` → `WorkflowEditorPage`.

### Module layout: `ui/src/modules/workflows/`
```
ui/WorkflowEditorPage.tsx          # layout + store owner
ui/editor/Canvas.tsx               # React Flow wrapper
ui/editor/NodePalette.tsx          # dnd source from step-types catalog
ui/editor/Inspector.tsx            # RJSF form for selected node
ui/editor/JsonSidebar.tsx          # pretty-printed JSON, two-way sync
ui/editor/TriggerDrawer.tsx        # RJSF for trigger (pinned __trigger__ node)
ui/editor/DryRunPanel.tsx          # sample payload + trace renderer
ui/editor/nodes/StepNode.tsx       # custom RF node with error badges
ui/editor/nodes/TriggerNode.tsx
ui/editor/edges/ResultCodeEdge.tsx # labelled, color-per-code
lib/graphModel.ts                  # graph JSON <-> RF nodes/edges
lib/editorStore.ts                 # Zustand: canonical graph + layout
lib/validationMapping.ts           # server errors -> node/edge badges
data/useStepTypes.ts
data/useTriggerTypes.ts
data/useWorkflowEditor.ts
data/useDryRun.ts
```

### Adapter extension
Extend `WorkflowPort` + `ui/src/platform/adapters/http/httpWorkflowAdapter.ts` with: `listStepTypes`, `listTriggerTypes`, `updateWorkflow`, `dryRun`. Add Zod schemas.

### Hybrid sync (avoid loops)
- Canonical state = graph JSON in Zustand. Two selectors derive (a) RF nodes/edges, (b) JSON text.
- `lastEditSource: 'canvas' | 'json'` flag.
- JSON textarea is controlled locally while user types; only pushes into store on successful parse (debounced 300ms).
- Canvas is dimmed with banner if JSON buffer is unparsed.
- Node positions live in a sibling `layout` map — not shipped to server, doesn't mark dirty.

### Graph <-> RF mapping (`lib/graphModel.ts`)
- `nodes[i]` → RF node `{ id, type: 'step', data: { stepType, config, errors } }`.
- Each `transitions[from][resultCode] = to` → RF edge with `label: resultCode`.
- Pinned synthetic `__trigger__` RF node with edges to root nodes. Stripped on serialize; its config becomes top-level `trigger`.
- `dependsOnNodeIds` recomputed from inverse edges.
- `branch_on_field`: result codes are derived from `config.cases[*].code`; Inspector prunes dangling edges with confirm toast.

### Validation UX (`lib/validationMapping.ts`)
- Call `POST /admin/workflows/validate` on save + manual "Validate".
- Map `error.path` like `nodes[2].config.url` → node errors → red-dot badge on `StepNode`.
- `UNREACHABLE_NODE` → dim node. `CYCLE` → red edges. `MISSING_RESULT_CODE` → dashed phantom edge stub.
- RJSF does instant client-side schema feedback in Inspector.

## PR slices (ordered)

1. **Backend catalog** (B1) — unblocks FE.
2. **Backend G6 trigger DTO** (B2).
3. **FE scaffolding** — deps, routes, empty `WorkflowEditorPage`, port/adapter extensions, RQ hooks, Zustand skeleton, JSON-only editing works.
4. **FE canvas read-only** — `graphModel`, Canvas renders existing workflows.
5. **FE palette + drag + connect + delete**.
6. **FE Inspector (RJSF) + TriggerDrawer** — two-way sync fully wired.
7. **FE save + validation mapping** — draft/publish, server error overlays, dirty tracking.
8. **Backend dry-run** (B3) — runner + adapters + endpoint + startup guard.
9. **FE DryRunPanel** — sample payload editor, trace, current-node animation.
10. **Polish** — keyboard shortcuts (del/dup), navigation-confirm-on-dirty, empty state.

## Risks / open questions

- **Dry-run JSONata reuse**: confirm `service/workflow/expression/JsonataExpressionEvaluator` is side-effect-free before reusing in runner.
- **RJSF theming**: default theme clashes with custom Tailwind design system. Start unstyled with custom field templates wrapping `ui/src/shared/ui/` primitives; full theme in follow-up.
- **Autosave**: plan assumes explicit save; if autosave wanted later, confirm optimistic-lock `expectedVersion` semantics upfront.
- **Trigger root rule**: one trigger only, or multiple roots allowed? Need product call — leaning single-trigger-per-workflow matches current engine.
- **Dry-run wait steps**: default stub outcome = first declared result code, user can override via `dryRunOverrides[nodeId]` to explore branches.

## Critical files

- Backend: `src/main/java/com/fuba/automation_engine/controller/AdminWorkflowController.java`, `src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepRegistry.java`, `service/workflow/dryrun/*` (new), `dto/CreateWorkflowRequest.java`, `dto/UpdateWorkflowRequest.java`.
- Frontend: `ui/src/app/router.tsx`, `ui/src/platform/adapters/http/httpWorkflowAdapter.ts`, new `ui/src/modules/workflows/` tree.

## Verification

- Backend: unit tests for catalog endpoints, `TriggerValidator`, `DryRunWorkflowRunner`; integration test creates workflow via builder-produced payload and dry-runs it end-to-end. Startup test asserts every step type has a dry-run adapter.
- Frontend: Vitest for `graphModel` round-trip (JSON → RF → JSON idempotent), `validationMapping` error placement. Playwright E2E: open editor, drag two nodes, connect result-code edge, fill config, dry-run, save draft, publish, confirm run inspector shows same graph.
- Manual: build the ASSIGNMENT_FOLLOWUP_SLA_V1 parity workflow in the UI from scratch and confirm it matches the Wave 2 hand-written JSON.
