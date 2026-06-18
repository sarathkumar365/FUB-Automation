# Phase 1 — Shape authority + matcher

> 2026-06-18 · Status: implemented, green, awaiting review (not yet committed).

## Goal

Introduce `ParsedTrigger` as the single interpreter of trigger shape, and make the
domain-event matcher read triggers through it — so the flat `{on, filter}` shape and
the new `anyOf` shape are handled by one code path. Backward-compatible and dormant:
`anyOf` triggers can't yet be saved (validator, Phase 2) or routed (router, Phase 3),
so this phase changes no runtime behaviour for existing flat triggers.

## How `ParsedTrigger` works

An immutable record built once from the raw trigger map:

```
record ParsedTrigger(List<TriggerEntry> entries, boolean isAnyOf, List<String> shapeErrors)
```

- `entries` — normalized `(on, filter)` pairs
- `isAnyOf` — which shape it came from
- `shapeErrors` — structural problems found while parsing (empty = structurally valid)

`from(Map)` is a single pass:

1. null trigger → one error, empty entries.
2. Detect shape by key presence (`on` vs `anyOf`).
3. Exactly-one check: `hasOn == hasAnyOf` → error (catches *both present* and *neither present* in one line).
4. `anyOf` → must be a non-empty array of objects; extract each `(on, filter)`; flag a missing `on`, duplicate kinds, or a stray `reactToEngineEvents` inside an entry.
5. flat → a single entry.

The key simplification: **flat is just a one-entry list**, so every reader sees "a list of
entries" and never branches on shape.

Input → output:

```
{ on:"person.created", filter:"person.kind='LEAD'" }
   → entries=[("person.created","person.kind='LEAD'")], isAnyOf=false, shapeErrors=[]

{ anyOf:[ {on:"person.created",filter:"a"}, {on:"person.state_changed",filter:"b"} ] }
   → entries=[("person.created","a"),("person.state_changed","b")], isAnyOf=true, shapeErrors=[]

{ anyOf:[] }
   → entries=[], isAnyOf=true, shapeErrors=["trigger.anyOf must be a non-empty array"]
```

Three readers, three methods:

- matcher (`DomainEventTriggerType`) → `entries()` (loop + evaluate)
- router (Phase 3) → `subscribesTo(kind)` (cheap pre-filter)
- validator (Phase 2) → `shapeErrors()` (structural gate) + its own per-kind checks over `entries()`

## Decisions & trade-offs

- **Value object, not loose functions or a behaviour-rich `Trigger`.** A record gives
  single-authority shape interpretation; the infra-bound work (JSONata eval, person-snapshot
  DB read, known-kind catalog) stays in the Spring services, which a per-config value object
  can't hold cleanly.
- **Lenient parse, strict errors.** `from()` always returns a usable object (best-effort
  entries) and reports problems *separately* in `shapeErrors`. Runtime works off `entries()`;
  save-time rejects on `shapeErrors()`. One parser, two consumers, no divergence.
- **`shapeErrors()` is structural only** (shape / non-empty array / duplicate kinds /
  top-level `reactToEngineEvents`). The known-kind catalog check and filter field-rules stay
  per-entry in the validator (Phase 2), where they already live for flat triggers — not
  duplicated here.
- **Matcher builds scope once, lazily**, and returns on the first matching entry.
- **`str()` uses `instanceof String`**, matching the validator's existing extraction;
  identical to the old matcher for any saveable trigger.

## Surprises

- **JSONata evaluator swallows errors to `null`** (known issue #10), so a bad filter already
  degrades to a non-match. The per-entry `try/catch` is therefore belt-and-suspenders — it only
  fires if `evaluatePredicate` ever *throws*. Both paths are tested (null + a mock that throws).
- **Router `:81` null-`on` guard** would skip an `anyOf` trigger (no top-level `on`) before it
  reaches the matcher — fixed in Phase 3, noted here so it isn't forgotten.

## Validation

- New tests: `ParsedTriggerTest` (11), `DomainEventTriggerTypeTest` +7 (anyOf match cases,
  both no-veto paths, scope-built-once).
- Targeted: 25/25 green. Full suite: 742 tests, 0 failures, 2 skipped, BUILD SUCCESS.

## Repo decisions impact

No — Phase 1 is a local feature concern (host trigger glue); it moves no architectural
boundary and needs no RD.

## Files

- new `service/workflow/trigger/ParsedTrigger.java`, `TriggerEntry.java`
- `service/workflow/trigger/DomainEventTriggerType.java` (matcher rewrite)
- tests `ParsedTriggerTest.java`, `DomainEventTriggerTypeTest.java`
