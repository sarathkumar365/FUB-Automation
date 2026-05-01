import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { SceneLayout } from '../modules/workflows-builder/model/layoutEngine'
import type { StoryboardScene } from '../modules/workflows-builder/model/graphAdapters'
import { Scene } from '../modules/workflows-builder/surfaces/storyboard/Scene'

/** Minimal scene + layout fixtures. The card is rendered inside a
 *  <foreignObject>, so we wrap it in an SVG in the test like the real
 *  viewer does. */
const scene: StoryboardScene = {
  id: 's1',
  kind: 'step',
  stepType: 'set_variable',
  config: {},
  isEntry: true,
}
const layout: SceneLayout = { id: 's1', x: 200, y: 120, width: 260, height: 110 }

describe('Scene selected-ring treatment (D6.1-a)', () => {
  it('uses the calm storyboard ring token, not --color-brand, when selected', () => {
    const { container } = render(
      <svg>
        <Scene scene={scene} layout={layout} selected onSelect={vi.fn()} />
      </svg>,
    )
    const card = container.querySelector('[data-builder-region="scene"]') as HTMLElement | null
    expect(card).toBeTruthy()
    const border = card!.style.border
    expect(border).toContain('--color-storyboard-card-ring-selected')
    // Regression guard: the harsh brand token must not come back.
    expect(border).not.toContain('--color-brand')
  })

  it('carries the .storyboard-scene-card class that suppresses the native mouse focus outline', () => {
    const { container } = render(
      <svg>
        <Scene scene={scene} layout={layout} selected onSelect={vi.fn()} />
      </svg>,
    )
    const card = container.querySelector('[data-builder-region="scene"]') as HTMLElement | null
    expect(card?.className).toContain('storyboard-scene-card')
  })

  it('uses the neutral card-border token when not selected', () => {
    const { container } = render(
      <svg>
        <Scene scene={scene} layout={layout} selected={false} onSelect={vi.fn()} />
      </svg>,
    )
    const card = container.querySelector('[data-builder-region="scene"]') as HTMLElement | null
    expect(card!.style.border).toContain('--color-storyboard-card-border')
    expect(card!.style.border).not.toContain('card-ring-selected')
  })
})
