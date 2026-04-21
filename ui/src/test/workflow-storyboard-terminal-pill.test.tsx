import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { describe, expect, it } from 'vitest'
import type { SceneLayout } from '../modules/workflows-builder/model/layoutEngine'
import { TerminalPill } from '../modules/workflows-builder/surfaces/storyboard/TerminalPill'

const parent: SceneLayout = { id: 'p', x: 300, y: 200, width: 260, height: 110 }

function renderSvg(ui: ReactElement) {
  return render(
    <svg width={1000} height={400}>
      {ui}
    </svg>,
  )
}

describe('TerminalPill', () => {
  it('exposes chip width that fits long result codes without overflow', () => {
    const { container } = renderSvg(
      <TerminalPill
        id="t-wide"
        from={parent}
        resultCode="communication_received"
        reason="lead responded"
        index={0}
        totalTerminals={1}
        side="right"
      />,
    )
    const group = container.querySelector('[data-terminal-id="t-wide"]')
    const width = Number(group?.getAttribute('data-chip-width') ?? '0')
    const label = 'communication_received → lead responded'
    // Using the shared chipMetrics constants (char width 7.2, padding 20)
    // the minimum width that avoids overflow is ceil(label.length * 7.2 + 20).
    const minimumViableWidth = Math.ceil(label.length * 7.2 + 20)
    expect(width).toBeGreaterThanOrEqual(minimumViableWidth)
    const rectWidth = Number(container.querySelector('rect')?.getAttribute('width') ?? '0')
    expect(rectWidth).toBe(width)
  })

  it('exposes data-terminal-side="right" and right-anchored text when side=right', () => {
    const { container } = renderSvg(
      <TerminalPill
        id="t1"
        from={parent}
        resultCode="ok"
        reason="done"
        index={0}
        totalTerminals={1}
        side="right"
      />,
    )
    const group = container.querySelector('[data-terminal-id="t1"]')
    expect(group?.getAttribute('data-terminal-side')).toBe('right')
    const text = container.querySelector('text')
    expect(text?.getAttribute('text-anchor')).toBe('start')
  })

  it('renders first-class kinds with a glyph prefix and kind-specific token triple (D6.2-c)', () => {
    const { container } = renderSvg(
      <TerminalPill
        id="t-success"
        from={parent}
        resultCode="success"
        reason="booked"
        index={0}
        totalTerminals={1}
        side="right"
      />,
    )
    const group = container.querySelector('[data-terminal-id="t-success"]')
    expect(group?.getAttribute('data-terminal-kind')).toBe('success')
    // Glyph prepended to the label.
    expect(container.querySelector('text')?.textContent).toBe('✓ success → booked')
    // Rect + text reference the semantic tokens, not the neutral fallback.
    const rect = container.querySelector('rect')
    expect(rect?.getAttribute('fill')).toContain('terminal-success-bg')
    expect(rect?.getAttribute('stroke')).toContain('terminal-success-border')
    expect(container.querySelector('text')?.getAttribute('fill')).toContain(
      'terminal-success-text',
    )
  })

  it('falls back to neutral chip tokens for unknown resultCodes (D6.4-a)', () => {
    const { container } = renderSvg(
      <TerminalPill
        id="t-custom"
        from={parent}
        resultCode="communication_received"
        reason="lead responded"
        index={0}
        totalTerminals={1}
        side="right"
      />,
    )
    const group = container.querySelector('[data-terminal-id="t-custom"]')
    expect(group?.getAttribute('data-terminal-kind')).toBe('neutral')
    // No glyph — label starts with the resultCode.
    expect(container.querySelector('text')?.textContent).toBe(
      'communication_received → lead responded',
    )
    const rect = container.querySelector('rect')
    expect(rect?.getAttribute('fill')).toContain('chip-neutral-bg')
  })

  it('exposes data-terminal-side="left" and end-anchored text when side=left', () => {
    const { container } = renderSvg(
      <TerminalPill
        id="t2"
        from={parent}
        resultCode="ok"
        reason="done"
        index={0}
        totalTerminals={1}
        side="left"
      />,
    )
    const group = container.querySelector('[data-terminal-id="t2"]')
    expect(group?.getAttribute('data-terminal-side')).toBe('left')
    const text = container.querySelector('text')
    expect(text?.getAttribute('text-anchor')).toBe('end')
    // pill rect should be to the LEFT of the parent's left edge.
    const rect = container.querySelector('rect')
    const rectX = Number(rect?.getAttribute('x') ?? '0')
    expect(rectX).toBeLessThan(parent.x - parent.width / 2)
  })
})
