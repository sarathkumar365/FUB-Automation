import { describe, expect, it } from 'vitest'
import { parseWorkflowGraph } from '../modules/workflows/ui/WorkflowDetailPage/lib/parseWorkflowGraph'

describe('parseWorkflowGraph', () => {
  it('returns ok with a parsed graph for a valid payload', () => {
    const result = parseWorkflowGraph({
      schemaVersion: 1,
      entryNode: 'start',
      nodes: [{ id: 'start', type: 'set_variable', config: {}, transitions: {} }],
    })
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.graph.entryNode).toBe('start')
      expect(result.graph.nodes).toHaveLength(1)
    }
  })

  it('returns issues when the schema does not match', () => {
    const result = parseWorkflowGraph({ schemaVersion: 2, entryNode: 'start', nodes: [] })
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.issues.length).toBeGreaterThan(0)
      const joined = result.issues.join('\n')
      expect(joined).toMatch(/schemaVersion/)
    }
  })

  it('returns a missing-payload issue when input is null or undefined', () => {
    const nullResult = parseWorkflowGraph(null)
    const undefinedResult = parseWorkflowGraph(undefined)
    expect(nullResult.ok).toBe(false)
    expect(undefinedResult.ok).toBe(false)
    if (!nullResult.ok) {
      expect(nullResult.issues[0]).toMatch(/missing/i)
    }
  })
})
