import { describe, expect, it } from 'vitest'
import type { StoryboardModel } from '../modules/workflows-builder/model/graphAdapters'
import type { StoryboardLayout } from '../modules/workflows-builder/model/layoutEngine'
import {
  computeTerminalPlacements,
  computeViewportBox,
  estimateTerminalPillWidth,
} from '../modules/workflows-builder/surfaces/storyboard/viewport'

function makeLayout(
  scenes: Array<{ id: string; x: number; y: number }>,
  width: number,
  height: number,
): StoryboardLayout {
  const map = new Map<string, { id: string; x: number; y: number; width: number; height: number }>()
  for (const scene of scenes) {
    map.set(scene.id, { id: scene.id, x: scene.x, y: scene.y, width: 260, height: 110 })
  }
  return { scenes: map, width, height }
}

describe('estimateTerminalPillWidth', () => {
  it('returns at least the minimum width for short labels', () => {
    expect(estimateTerminalPillWidth('ok', 'done')).toBe(140)
  })

  it('grows with label length', () => {
    const narrow = estimateTerminalPillWidth('ok', 'short')
    const wide = estimateTerminalPillWidth('timeout_expired_exhaustively', 'gave_up_eventually')
    expect(wide).toBeGreaterThan(narrow)
  })
})

describe('computeTerminalPlacements', () => {
  it('assigns opposite sides to terminals whose parents sit on opposite halves of the graph', () => {
    const model: StoryboardModel = {
      scenes: [
        { id: 'left', kind: 'step', stepType: 's', config: {}, isEntry: true },
        { id: 'right', kind: 'step', stepType: 's', config: {}, isEntry: false },
      ],
      exits: [],
      terminals: [
        { id: 't-left', fromSceneId: 'left', resultCode: 'ok', reason: 'done' },
        { id: 't-right', fromSceneId: 'right', resultCode: 'err', reason: 'bad' },
      ],
      sceneOrder: ['left', 'right'],
    }
    const layout = makeLayout(
      [
        { id: 'left', x: 100, y: 100 },
        { id: 'right', x: 500, y: 100 },
      ],
      600,
      200,
    )

    const placements = computeTerminalPlacements(model, layout)
    const byId = new Map(placements.map((p) => [p.terminalId, p]))
    expect(byId.get('t-left')?.side).toBe('left')
    expect(byId.get('t-right')?.side).toBe('right')
  })

  it('stacks multiple terminals for the same parent with vertical offsets', () => {
    const model: StoryboardModel = {
      scenes: [{ id: 'a', kind: 'step', stepType: 's', config: {}, isEntry: true }],
      exits: [],
      terminals: [
        { id: 't1', fromSceneId: 'a', resultCode: 'ok', reason: 'done' },
        { id: 't2', fromSceneId: 'a', resultCode: 'err', reason: 'bad' },
      ],
      sceneOrder: ['a'],
    }
    const layout = makeLayout([{ id: 'a', x: 300, y: 100 }], 600, 200)
    const placements = computeTerminalPlacements(model, layout)
    expect(placements).toHaveLength(2)
    expect(placements[0].offsetY).not.toBe(placements[1].offsetY)
  })
})

describe('computeViewportBox', () => {
  it('returns (almost) Dagre dimensions when there are no terminals', () => {
    const layout = makeLayout([{ id: 'a', x: 300, y: 100 }], 600, 200)
    const box = computeViewportBox({ layout, terminalPlacements: [] })
    expect(box.x).toBeLessThanOrEqual(0)
    expect(box.width).toBeGreaterThanOrEqual(600)
    expect(box.height).toBeGreaterThanOrEqual(200)
  })

  it('produces negative x when a left-side terminal extends past 0', () => {
    const layout = makeLayout([{ id: 'a', x: 60, y: 100 }], 600, 200)
    const placements = computeTerminalPlacements(
      {
        scenes: [{ id: 'a', kind: 'step', stepType: 's', config: {}, isEntry: true }],
        exits: [],
        terminals: [{ id: 't', fromSceneId: 'a', resultCode: 'ok', reason: 'done' }],
        sceneOrder: ['a'],
      },
      layout,
    )
    const box = computeViewportBox({ layout, terminalPlacements: placements })
    expect(box.x).toBeLessThan(0)
  })

  it('grows width to include a right-side terminal extent', () => {
    const layout = makeLayout([{ id: 'a', x: 540, y: 100 }], 600, 200)
    const placements = computeTerminalPlacements(
      {
        scenes: [{ id: 'a', kind: 'step', stepType: 's', config: {}, isEntry: true }],
        exits: [],
        terminals: [{ id: 't', fromSceneId: 'a', resultCode: 'ok', reason: 'done' }],
        sceneOrder: ['a'],
      },
      layout,
    )
    const box = computeViewportBox({ layout, terminalPlacements: placements })
    // right edge = viewBoxX + viewBoxWidth should exceed the layout width.
    expect(box.x + box.width).toBeGreaterThan(600)
  })
})
