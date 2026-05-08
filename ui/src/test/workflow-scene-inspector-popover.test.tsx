import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SceneLayout } from '../modules/workflows-builder/model/layoutEngine'
import type { Graph } from '../modules/workflows-builder/state/runtimeContract'
import {
  POPOVER_MAX_HEIGHT,
  POPOVER_WIDTH,
} from '../modules/workflows/ui/WorkflowDetailPage/StoryboardTab/constants'
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

  it('exposes the inspector body with a scrollable inner wrapper', () => {
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
    const body = screen.getByTestId('workflow-scene-inspector-body')
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
  })

  it('renders structured config values via the shared JsonViewer', () => {
    const structuredGraph: Graph = {
      schemaVersion: 1,
      entryNode: 'n1',
      nodes: [
        {
          id: 'n1',
          type: 'set_variable',
          config: { nested: { a: 1, b: [1, 2] } },
          transitions: {},
        },
      ],
    }
    render(
      <SceneInspectorPopover
        graph={structuredGraph}
        sceneId="n1"
        sceneLayout={baseLayout}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={vi.fn()}
      />,
    )
    // The JsonViewer exposes a Copy button and a <pre> with the stringified JSON.
    expect(screen.getByRole('button', { name: /copy/i })).toBeInTheDocument()
    const expected = JSON.stringify({ a: 1, b: [1, 2] }, null, 2)
    expect(screen.getByText((_, element) => element?.tagName === 'PRE' && (element.textContent ?? '').includes(expected))).toBeInTheDocument()
  })

  it('renders short scalar config values inline (D4.1-a)', () => {
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
    // "channel" label + "#ops" value are short scalars → inline FieldRow.
    // Both should surface as siblings under a single inline-grid container.
    const label = screen.getByText('channel')
    const value = screen.getByText('#ops')
    expect(label.parentElement).toBe(value.parentElement)
    expect(label.parentElement?.className ?? '').toMatch(/grid/)
  })

  it('renders long plain string values stacked with a Show more toggle (D4.4-a)', () => {
    const longText = 'a'.repeat(80)
    const longGraph: Graph = {
      schemaVersion: 1,
      entryNode: 'n1',
      nodes: [
        {
          id: 'n1',
          type: 'slack_notify',
          config: { note: longText },
          transitions: {},
        },
      ],
    }
    // Stub overflow so ClampedText shows the toggle.
    Object.defineProperty(Element.prototype, 'scrollHeight', {
      configurable: true,
      get() {
        if (this instanceof HTMLElement && this.dataset.testid === 'clamped-text-body') return 200
        return 0
      },
    })
    Object.defineProperty(Element.prototype, 'clientHeight', {
      configurable: true,
      get() {
        if (this instanceof HTMLElement && this.dataset.testid === 'clamped-text-body') return 80
        return 0
      },
    })
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
    expect(screen.getByTestId('clamped-text-body').textContent).toContain(longText)
    expect(screen.getByRole('button', { name: /show more/i })).toBeInTheDocument()
    // Reset stubs.
    Object.defineProperty(Element.prototype, 'scrollHeight', { configurable: true, value: 0 })
    Object.defineProperty(Element.prototype, 'clientHeight', { configurable: true, value: 0 })
  })

  it('renders templating strings in monospace with a copy button (D4.2-b / D4.9-b)', () => {
    const tplGraph: Graph = {
      schemaVersion: 1,
      entryNode: 'n1',
      nodes: [
        {
          id: 'n1',
          type: 'slack_notify',
          config: { greeting: 'Hello {{ lead.name }}' },
          transitions: {},
        },
      ],
    }
    render(
      <SceneInspectorPopover
        graph={tplGraph}
        sceneId="n1"
        sceneLayout={baseLayout}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={vi.fn()}
      />,
    )
    const valueCell = screen.getByText('Hello {{ lead.name }}')
    expect(valueCell.className).toMatch(/font-mono/)
    // A copy button (distinct from the JsonViewer one, which isn't rendered here).
    expect(screen.getByRole('button', { name: /copy/i })).toBeInTheDocument()
  })

  it('renders URL config values as copy-only — no anchor tag (D4.5-c)', () => {
    const urlGraph: Graph = {
      schemaVersion: 1,
      entryNode: 'n1',
      nodes: [
        {
          id: 'n1',
          type: 'http_request',
          config: { url: 'https://api.followupboss.com/v1/people/123' },
          transitions: {},
        },
      ],
    }
    const { container } = render(
      <SceneInspectorPopover
        graph={urlGraph}
        sceneId="n1"
        sceneLayout={baseLayout}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={vi.fn()}
      />,
    )
    const text = screen.getByText('https://api.followupboss.com/v1/people/123')
    expect(text.className).toMatch(/font-mono/)
    expect(screen.getByRole('button', { name: /copy/i })).toBeInTheDocument()
    // Deliberately no <a> — navigation affordance is out of scope per D4.5-c.
    expect(container.querySelector('a[href^="http"]')).toBeNull()
  })

  it('renders each transition as a stacked card with chip on top and target below (D4.6-a)', () => {
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
    const rows = screen.getAllByTestId('workflow-scene-inspector-transition-row')
    for (const row of rows) {
      // Card layout: column-stacked, with padding + rounded border.
      expect(row.style.flexDirection).toBe('column')
      expect(row.style.borderRadius).not.toBe('')
      // Chip and target exist as siblings inside the card.
      expect(within(row).getByText(/^on /)).toBeInTheDocument()
      expect(within(row).getByTestId('workflow-scene-inspector-transition-target')).toBeInTheDocument()
    }
  })

  it('uses the widened popover envelope per D4.7-c / D4.8-a', () => {
    // Sanity: constants carry the bumped values the inspector renders with.
    expect(POPOVER_WIDTH).toBe(420)
    expect(POPOVER_MAX_HEIGHT).toBe(560)
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
    // Width is applied via style; max-height applied to the inner body.
    expect(popover.style.width).toBe(`${POPOVER_WIDTH}px`)
  })

  it('invokes onClose when the close button is clicked', () => {
    const onClose = vi.fn()
    render(
      <SceneInspectorPopover
        graph={graph}
        sceneId="n1"
        sceneLayout={baseLayout}
        canvasWidth={1000}
        canvasHeight={400}
        onClose={onClose}
      />,
    )
    const closeButton = screen.getByRole('button', { name: /close inspector/i })
    fireEvent.click(closeButton)
    expect(onClose).toHaveBeenCalled()
  })
})
