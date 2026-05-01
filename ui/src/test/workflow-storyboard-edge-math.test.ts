import { describe, expect, it } from 'vitest'
import {
  cubicBezierPoint,
  edgeLabelT,
} from '../modules/workflows-builder/surfaces/storyboard/edgeMath'

describe('edgeLabelT', () => {
  it('centers a lone edge label at 0.5', () => {
    expect(edgeLabelT(0, 1)).toBeCloseTo(0.5, 10)
  })

  it('produces distinct fractions for distinct edge indices', () => {
    const values = [0, 1, 2].map((i) => edgeLabelT(i, 3))
    expect(new Set(values).size).toBe(values.length)
  })

  it('keeps labels inside the 30%-70% window', () => {
    for (const count of [2, 3, 5]) {
      for (let i = 0; i < count; i += 1) {
        const t = edgeLabelT(i, count)
        expect(t).toBeGreaterThanOrEqual(0.3 - 1e-9)
        expect(t).toBeLessThanOrEqual(0.7 + 1e-9)
      }
    }
  })
})

describe('cubicBezierPoint', () => {
  it('returns the start point at t=0 and the end point at t=1', () => {
    const p0 = { x: 0, y: 0 }
    const p1 = { x: 0, y: 10 }
    const p2 = { x: 10, y: 10 }
    const p3 = { x: 10, y: 20 }
    expect(cubicBezierPoint(0, p0, p1, p2, p3)).toEqual(p0)
    const last = cubicBezierPoint(1, p0, p1, p2, p3)
    expect(last.x).toBeCloseTo(p3.x)
    expect(last.y).toBeCloseTo(p3.y)
  })
})
