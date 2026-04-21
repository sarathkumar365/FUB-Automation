/**
 * Render decision for a single config value in the scene inspector.
 *
 * Kept deliberately simple: the popover renders a compact one-line chip for
 * scalars/templating strings and delegates structured values (object/array)
 * to the shared `JsonViewer`. See `ConfigRow` for the branching.
 */

export type FormatConfigValueResult =
  | { kind: 'empty' }
  | { kind: 'scalar'; text: string; mono: boolean }
  | { kind: 'structured'; value: object }

/**
 * Classify a config value into one of three render buckets:
 *   - `empty`: null/undefined → em dash
 *   - `scalar`: string/number/boolean → inline chip (mono for code-ish strings)
 *   - `structured`: object/array → JsonViewer
 */
export function formatConfigValue(value: unknown): FormatConfigValueResult {
  if (value === null || value === undefined) {
    return { kind: 'empty' }
  }
  if (typeof value === 'string') {
    const mono = value.startsWith('$') || /[${}]/.test(value)
    return { kind: 'scalar', text: value, mono }
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return { kind: 'scalar', text: String(value), mono: true }
  }
  if (typeof value === 'object') {
    return { kind: 'structured', value: value as object }
  }
  // Fallback for exotic types (symbol, function, bigint). Stringify defensively.
  try {
    return { kind: 'scalar', text: String(value), mono: true }
  } catch {
    return { kind: 'scalar', text: '…', mono: false }
  }
}
