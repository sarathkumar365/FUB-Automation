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

  it('keeps the outer popover card overflow visible so chips do not clip', () => {
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
    const popover = screen.getByTestId('workflow-scene-inspector')
    expect(popover.style.overflow).toBe('visible')
    const body = screen.getByTestId('workflow-scene-inspector-body')
    // Inner wrapper handles scroll + supplies breathing room from the border.
    expect(body.style.overflowY).toBe('auto')
    expect(body.style.padding).toMatch(/18px 20px/)
    expect(body.style.minWidth).toBe('0px')
  })

  it('renders transitions as a stacked list with one row per transition', () => {
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
    const list = screen.getByTestId('workflow-scene-inspector-transitions')
    expect(list.getAttribute('data-layout')).toBe('stack')
    expect(list.style.flexDirection).toBe('column')
    const rows = screen.getAllByTestId('workflow-scene-inspector-transition-row')
    // graph n1 has two transitions: default→n2 (node) and timeout→terminal.
    expect(rows).toHaveLength(2)
    // Rows must not clip long target text with ellipsis.
    for (const row of rows) {
      expect(row.style.textOverflow).not.toBe('ellipsis')
    }
  })

  it('renders long transition resultCodes and targets without truncation', () => {
    const longCode = 'on_communication_received_and_classified_as_incoming_reply'
    const longTarget = 'handle_communication_response_case_with_long_identifier_name'
    const longGraph: Graph = {
      schemaVersion: 1,
      entryNode: 'n1',
      nodes: [
        {
          id: 'n1',
          type: 'slack_notify',
          config: {},
          transitions: { [longCode]: [longTarget] },
        },
        { id: longTarget, type: 'set_variable', config: {}, transitions: {} },
      ],
    }
    render(
      <SceneInspectorPopover
        graph={longGraph}
        sceneId="n1"
        sceneLayout={baseLayout}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={vi.fn()}
      />,
    )
    const target = screen.getByTestId('workflow-scene-inspector-transition-target')
    // Full text must be present — no ellipsis character, no overflow:hidden on the value cell.
    expect(target.textContent).toContain(longTarget)
    expect(target.textContent).not.toContain('…')
    expect(target.style.textOverflow).not.toBe('ellipsis')
    expect(target.style.overflow).not.toBe('hidden')
    expect(target.style.overflowWrap).toBe('anywhere')
    // Also verify the resultCode side reads in full.
    expect(screen.getByText(new RegExp(longCode))).toBeInTheDocument()
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

  it('treats scenes as right-side when viewBoxOriginX shifts them past the pixel midline', () => {
    // Scene user-space x=200, viewBox origin -200 → pixel x = 400. Canvas
    // pixel width 600 → midline 300 → scene on right half → popover left.
    render(
      <SceneInspectorPopover
        graph={graph}
        sceneId="n1"
        sceneLayout={{ ...baseLayout, x: 200 }}
        canvasWidth={600}
        canvasHeight={400}
        viewBoxOriginX={-200}
        onClose={vi.fn()}
      />,
    )
    const popover = screen.getByTestId('workflow-scene-inspector')
    expect(popover.getAttribute('data-popover-side')).toBe('left')
    // left position must be in pixel space (so positive, reasonable).
    const style = popover.getAttribute('style') ?? ''
    expect(style).toMatch(/left:/)
  })
})
