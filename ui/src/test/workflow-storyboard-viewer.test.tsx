import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  graphToStoryboard,
} from '../modules/workflows-builder/model/graphAdapters'
import { layoutStoryboard, SCENE_WIDTH } from '../modules/workflows-builder/model/layoutEngine'
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

  it('renders the formatted scene title with accent metadata and a type pill', () => {
    const { container } = render(<StoryboardViewer graph={graph} trigger={null} />)
    const firstScene = container.querySelector('[data-builder-region="scene"][data-scene-id="a"]')
    expect(firstScene).toBeTruthy()
    expect(firstScene?.getAttribute('data-accent')).toBeTruthy()
    expect(firstScene?.getAttribute('data-step-type')).toBe('set_variable')
    const name = firstScene?.querySelector('[data-builder-region="scene-name"]')
    // Formatted title for set_variable is "Set x" (from cardFormatters).
    expect(name?.textContent).toBe('Set x')
    const typePill = firstScene?.querySelector('[data-builder-region="scene-type"]')
    expect(typePill?.textContent).toContain('set_variable')
    // Entry scene carries the "entry · " prefix.
    expect(typePill?.textContent).toContain('entry')
  })

  it('renders scene cards with neutral pill styling (no colored accent fill)', () => {
    const { container } = render(<StoryboardViewer graph={graph} trigger={null} />)
    const scene = container.querySelector('[data-builder-region="scene"][data-scene-id="a"]')
    // data-accent still present so later features can read category.
    expect(scene?.getAttribute('data-accent')).toBeTruthy()
    const pill = scene?.querySelector('[data-builder-region="scene-type"]') as HTMLElement | null
    expect(pill).toBeTruthy()
    // Neutral tokens — muted slate background + slate text, no colored tinting.
    expect(pill?.style.background).toMatch(/rgba\(100,\s*116,\s*139/)
    expect(pill?.style.color).toMatch(/#475569|rgb\(71,\s*85,\s*105\)/i)
  })

  it('renders the trigger scene with a humane title, not "unknown"', () => {
    const { container } = render(
      <StoryboardViewer
        graph={graph}
        trigger={{ type: 'webhook', config: { path: '/hooks/demo' } }}
      />,
    )
    const triggerScene = container.querySelector(
      '[data-builder-region="scene"][data-scene-id="__trigger__"]',
    )
    expect(triggerScene).toBeTruthy()
    expect(triggerScene?.getAttribute('data-accent')).toBe('trigger')
    const name = triggerScene?.querySelector('[data-builder-region="scene-name"]')
    expect(name?.textContent).toContain('Trigger')
    expect(name?.textContent?.toLowerCase()).not.toContain('unknown')
  })

  it('truncates long formatted titles while keeping the scene within SCENE_WIDTH', () => {
    const longId = 'node_with_a_very_long_descriptive_identifier'
    // set_variable uses the config.name for the title; a long name exercises
    // the ellipsis guard added when the summary/id rows were removed.
    const longGraph: Graph = {
      schemaVersion: 1,
      entryNode: longId,
      nodes: [
        {
          id: longId,
          type: 'set_variable',
          config: { name: 'a_very_long_variable_name_that_should_definitely_truncate' },
          transitions: {},
        },
      ],
    }
    const layout = layoutStoryboard(graphToStoryboard(longGraph, null))
    const sceneLayout = layout.scenes.get(longId)
    expect(sceneLayout?.width).toBe(SCENE_WIDTH)
    const { container } = render(<StoryboardViewer graph={longGraph} trigger={null} />)
    const name = container.querySelector(
      '[data-builder-region="scene-name"]',
    ) as HTMLElement | null
    // The title element must enforce truncation so long labels cannot blow out
    // the card (there is no summary line beneath to absorb overflow).
    expect(name?.style.textOverflow).toBe('ellipsis')
    expect(name?.style.overflow).toBe('hidden')
    expect(name?.style.whiteSpace).toBe('nowrap')
  })
})
