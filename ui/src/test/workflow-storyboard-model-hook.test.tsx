import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useStoryboardModel } from '../modules/workflows-builder/surfaces/storyboard/useStoryboardModel'
import type { Graph } from '../modules/workflows-builder/state/runtimeContract'

const graph: Graph = {
  schemaVersion: 1,
  entryNode: 'a',
  nodes: [
    { id: 'a', type: 'set_variable', config: {}, transitions: { default: ['b'] } },
    { id: 'b', type: 'slack_notify', config: {}, transitions: {} },
  ],
}

describe('useStoryboardModel', () => {
  it('returns a model with a scene per node and a layout entry per scene', () => {
    const { result } = renderHook(() => useStoryboardModel(graph, null))
    expect(result.current.model.scenes).toHaveLength(graph.nodes.length)
    expect(result.current.layout.scenes.size).toBe(graph.nodes.length)
  })

  it('is referentially stable across re-renders with the same inputs', () => {
    const { result, rerender } = renderHook(
      ({ g, t }: { g: Graph; t: Record<string, unknown> | null }) => useStoryboardModel(g, t),
      { initialProps: { g: graph, t: null } },
    )
    const firstModel = result.current.model
    const firstLayout = result.current.layout
    rerender({ g: graph, t: null })
    expect(result.current.model).toBe(firstModel)
    expect(result.current.layout).toBe(firstLayout)
  })

  it('produces a deterministic top-to-bottom layout for a linear graph', () => {
    const { result } = renderHook(() => useStoryboardModel(graph, null))
    const parent = result.current.layout.scenes.get('a')
    const child = result.current.layout.scenes.get('b')
    expect(parent && child).toBeTruthy()
    if (parent && child) {
      expect(child.y).toBeGreaterThan(parent.y)
    }
  })
})
