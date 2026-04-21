/**
 * Render-kind classifier for a single config value in the scene inspector.
 *
 * Produces a discriminated union so `ConfigRow` can dispatch each kind to
 * the right recipe without re-inspecting the value. Decisions behind the
 * bucket choices:
 *   - **empty**      — null/undefined → em dash (inline). Covers the "no
 *                      value set" signal without treating it as text.
 *   - **scalar**     — booleans, numbers, and strings ≤
 *                      `FIELD_ROW_LONG_THRESHOLD` characters that are
 *                      neither URL nor templating. Rendered inline by
 *                      `FieldRow` (D4.1-a).
 *   - **templating** — strings that look like an expression: start with
 *                      `$` (JSONata) or contain `{{`/`}}` (handlebars-ish
 *                      binding). Rendered stacked + monospace +
 *                      `CopyableValue` (D4.2-b / D4.9-b).
 *   - **url**        — strings starting with `http://` or `https://`.
 *                      Rendered stacked + `CopyableValue` with copy-only
 *                      affordance; explicitly NOT an anchor/link
 *                      (D4.5-c). Mailto/tel are intentionally not
 *                      special-cased.
 *   - **plain**      — long plain strings (> threshold). Rendered stacked
 *                      with `pre-wrap`, clamped at 4 lines with "Show
 *                      more" (D4.4-a / D4.10-b / D4.9-b: no copy).
 *   - **structured** — arrays/objects. Rendered via shared `JsonViewer`.
 *
 * The threshold itself lives in `shared/ui/recipes/FieldRow.ts` so the
 * recipe-layer default and the inspector-layer dispatcher share one
 * source of truth.
 */
import { FIELD_ROW_LONG_THRESHOLD } from '../../../../../../shared/ui/recipes/FieldRow'

export type FormatConfigValueResult =
  | { kind: 'empty' }
  | { kind: 'scalar'; text: string }
  | { kind: 'templating'; text: string }
  | { kind: 'url'; text: string }
  | { kind: 'plain'; text: string }
  | { kind: 'structured'; value: object }

const URL_PREFIX = /^https?:\/\//i
const TEMPLATING_PATTERN = /\{\{|\}\}|^\$/

/** Returns true when `text` starts with `http://` or `https://`. Mailto/tel
 *  are intentionally not detected — workflow config URLs are almost always
 *  web APIs or webhook endpoints, and a false-positive copy affordance on
 *  an arbitrary `mailto:` would be misleading. */
export function isUrlShaped(text: string): boolean {
  return URL_PREFIX.test(text)
}

/** Returns true when `text` looks like a templating expression: starts
 *  with `$` (JSONata) or contains `{{` / `}}` (handlebars-style binding). */
export function isTemplating(text: string): boolean {
  return TEMPLATING_PATTERN.test(text)
}

export function formatConfigValue(value: unknown): FormatConfigValueResult {
  if (value === null || value === undefined) {
    return { kind: 'empty' }
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return { kind: 'scalar', text: String(value) }
  }
  if (typeof value === 'string') {
    if (isUrlShaped(value)) return { kind: 'url', text: value }
    if (isTemplating(value)) return { kind: 'templating', text: value }
    if (value.length > FIELD_ROW_LONG_THRESHOLD || value.includes('\n')) {
      return { kind: 'plain', text: value }
    }
    return { kind: 'scalar', text: value }
  }
  if (typeof value === 'object') {
    return { kind: 'structured', value: value as object }
  }
  // Exotic types (symbol, bigint, function): stringify defensively as scalar.
  try {
    return { kind: 'scalar', text: String(value) }
  } catch {
    return { kind: 'scalar', text: '…' }
  }
}
