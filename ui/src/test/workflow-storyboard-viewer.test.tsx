import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  graphToStoryboard,
} from '../modules/workflows-builder/model/graphAdapters'
import { layoutStoryboard } from '../modules/workflows-builder/model/layoutEngine'
import { StoryboardViewer } from '../modules/workflows-builder/surfaces/storyboard/StoryboardViewer'
import type { Graph } from '../modules/workflows-builder/state/runtimeContract'

const graph: Graph = {
  schemaVersion: 1,
  entryNode: 'a',
  nodes: [
    {
      id: 'a',
      type: 'set_variable',
      config: { name: 'x', value: 1 },
      transitions: { default: ['b'] },
    },
    { id: 'b', type: 'slack_notify', config: { channel: 'ops' }, transitions: {} },
  ],
}

describe('StoryboardViewer', () => {
  it('renders an SVG with a scene element per graph node', () => {
    const { container } = render(<StoryboardViewer graph={graph} trigger={null} />)
    expect(container.querySelector('svg')).toBeTruthy()
    const scenes = container.querySelectorAll('[data-builder-region="scene"]')
    expect(scenes).toHaveLength(graph.nodes.length)
  })

  it('invokes onSelectScene when a scene is clicked', () => {
    const handler = vi.fn()
    const { container } = render(
      <StoryboardViewer graph={graph} trigger={null} onSelectScene={handler} />,
    )
    const scenes = container.querySelectorAll<HTMLElement>('[data-builder-region="scene"]')
    scenes[0].click()
    expect(handler).toHaveBeenCalledWith('a')
  })

  it('lays out child scenes below their parent (vertical TB)', () => {
    const layout = layoutStoryboard(graphToStoryboard(graph, null))
    const parent = layout.scenes.get('a')
    const child = layout.scenes.get('b')
    expect(parent).toBeDefined()
    expect(child).toBeDefined()
    if (parent && child) {
      expect(child.y).toBeGreaterThan(parent.y)
    }
  })

  it('no longer renders the storyboard spine', () => {
    const { container } = render(<StoryboardViewer graph={graph} trigger={null} />)
    expect(container.querySelector('[data-builder-region="spine"]')).toBeNull()
  })
})
