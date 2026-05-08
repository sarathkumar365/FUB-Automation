/**
 * Extract the `type` discriminator from a workflow trigger blob.
 *
 * Triggers are stored as free-form `Record<string, unknown>` (validated by the
 * backend), so the UI has to read `trigger.type` defensively: missing, wrong
 * type, and empty string all collapse to `null` at the call site. Centralised
 * here so the UI has a single source of truth for "how do we read a trigger
 * type" — important because the shape is not enforced by TypeScript at the
 * boundary.
 */
export function readTriggerType(trigger: Record<string, unknown> | null | undefined): string | null {
  if (!trigger) return null
  const candidate = trigger.type
  return typeof candidate === 'string' && candidate.length > 0 ? candidate : null
}
