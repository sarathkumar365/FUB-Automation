import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SceneLayout } from '../modules/workflows-builder/model/layoutEngine'
import type { Graph } from '../modules/workflows-builder/state/runtimeContract'
import { SceneInspectorPopover } from '../modules/workflows/ui/WorkflowDetailPage/StoryboardTab/SceneInspectorPopover'

const graph: Graph = {
  schemaVersion: 1,
  entryNode: 'n1',
  nodes: [
    {
      id: 'n1',
      type: 'slack_notify',
      config: { channel: '#ops' },
      transitions: { default: ['n2'], timeout: { terminal: 'gave_up' } },
    },
    { id: 'n2', type: 'set_variable', config: {}, transitions: {} },
  ],
}

const baseLayout: SceneLayout = {
  id: 'n1',
  x: 200,
  y: 120,
  width: 260,
  height: 110,
}

describe('SceneInspectorPopover', () => {
  it('renders the selected node config and transitions', () => {
    render(
      <SceneInspectorPopover
        graph={graph}
        sceneId="n1"
        sceneLayout={baseLayout}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={vi.fn()}
      />,
    )
    expect(screen.getByText('channel')).toBeInTheDocument()
    expect(screen.getByText('#ops')).toBeInTheDocument()
    expect(screen.getByText(/on default/)).toBeInTheDocument()
    expect(screen.getByText(/on timeout/)).toBeInTheDocument()
  })

  it('opens to the right when the scene sits on the left half of the canvas', () => {
    render(
      <SceneInspectorPopover
        graph={graph}
        sceneId="n1"
        sceneLayout={{ ...baseLayout, x: 200 }}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={vi.fn()}
      />,
    )
    const popover = screen.getByTestId('workflow-scene-inspector')
    expect(popover.getAttribute('data-popover-side')).toBe('right')
  })

  it('opens to the left when the scene sits on the right half of the canvas', () => {
    render(
      <SceneInspectorPopover
        graph={graph}
        sceneId="n1"
        sceneLayout={{ ...baseLayout, x: 800 }}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={vi.fn()}
      />,
    )
    const popover = screen.getByTestId('workflow-scene-inspector')
    expect(popover.getAttribute('data-popover-side')).toBe('left')
  })
})
