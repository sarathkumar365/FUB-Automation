/**
 * Parse a raw workflow graph payload against the runtime contract.
 *
 * Returns a discriminated union so callers can branch on success/failure
 * without throwing. Zod issues are flattened into readable strings intended
 * for an end-user facing error panel.
 */
import { graphSchema, type Graph } from '../../../../workflows-builder/state/runtimeContract'

export type ParseWorkflowGraphResult =
  | { ok: true; graph: Graph }
  | { ok: false; issues: string[] }

export function parseWorkflowGraph(raw: unknown): ParseWorkflowGraphResult {
  if (raw === null || raw === undefined) {
    return { ok: false, issues: ['Graph payload is missing.'] }
  }

  const parsed = graphSchema.safeParse(raw)
  if (parsed.success) {
    return { ok: true, graph: parsed.data }
  }

  const issues = parsed.error.issues.map((issue) => {
    const path = issue.path.length > 0 ? issue.path.join('.') : '<root>'
    return `${path}: ${issue.message}`
  })
  return { ok: false, issues }
}
