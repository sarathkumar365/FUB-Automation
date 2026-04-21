/**
 * Terminal markers — rendered as small pills hanging off a scene's side edge,
 * stacked vertically. Each pill represents a resultCode that maps to
 * `{ terminal: reason }` in the graph: "workflow ends here" outcomes without
 * adding extra scene nodes to the canvas.
 *
 * Side is determined by the viewer (based on the parent scene's position
 * relative to the graph's horizontal midline) so pills on the left branch fly
 * outward to the left and pills on the right branch fly outward to the right.
 * This prevents a left-branch pill from cutting through scenes on the right.
 *
 * Per D6.2-c / D6.3-a / D6.4-a: the pill's glyph and colour triple come from
 * `./terminalKind.resolveTerminalKind(resultCode)`. First-class kinds
 * (`success` / `failure` / `skipped` / `noop`) get a distinguishing prefix
 * glyph + semantic token triple; unknown/custom codes fall back to the
 * existing neutral palette.
 */
import type { SceneLayout } from '../../model/layoutEngine'
import { CHIP_FONT_SIZE, CHIP_PADDING_X } from './chipMetrics'
import { resolveTerminalKind, tokensForKind } from './terminalKind'
import { estimateTerminalPillWidth, TERMINAL_PILL_GAP, TERMINAL_PILL_ROW_SPACING } from './viewport'

export interface TerminalPillProps {
  id: string
  from: SceneLayout
  resultCode: string
  reason: string
  /** Zero-based position among terminals for the same `from` scene. */
  index: number
  /** Total terminals attached to the same `from` scene (for centering). */
  totalTerminals: number
  /** Which side of the parent the pill flies toward. */
  side: 'left' | 'right'
}

export function TerminalPill({
  id,
  from,
  resultCode,
  reason,
  index,
  totalTerminals,
  side,
}: TerminalPillProps) {
  const kind = resolveTerminalKind(resultCode)
  const tokens = tokensForKind(kind)
  const anchorY = from.y
  const pillY = anchorY + (index - (totalTerminals - 1) / 2) * TERMINAL_PILL_ROW_SPACING
  const label = tokens.glyph
    ? `${tokens.glyph} ${resultCode} → ${reason}`
    : `${resultCode} → ${reason}`
  const width = estimateTerminalPillWidth(resultCode, reason)
  const halfPad = CHIP_PADDING_X / 2

  const anchorX =
    side === 'right' ? from.x + from.width / 2 : from.x - from.width / 2
  const pillLeft = side === 'right' ? anchorX + TERMINAL_PILL_GAP : anchorX - TERMINAL_PILL_GAP - width
  const lineEndX = side === 'right' ? pillLeft : pillLeft + width
  const textX = side === 'right' ? pillLeft + halfPad : pillLeft + width - halfPad
  const textAnchor = side === 'right' ? 'start' : 'end'

  return (
    <g
      data-builder-region="terminal-pill"
      data-terminal-id={id}
      data-terminal-side={side}
      data-terminal-kind={kind}
      data-chip-width={width}
    >
      <line
        x1={anchorX}
        y1={anchorY}
        x2={lineEndX}
        y2={pillY}
        stroke="var(--color-storyboard-edge-dashed)"
        strokeWidth={1.25}
        strokeDasharray="3 3"
      />
      <rect
        x={pillLeft}
        y={pillY - 11}
        width={width}
        height={22}
        rx={11}
        ry={11}
        fill={tokens.bg}
        stroke={tokens.border}
      />
      <text
        x={textX}
        y={pillY + 4}
        fontSize={CHIP_FONT_SIZE}
        fontFamily="var(--font-mono)"
        fill={tokens.text}
        textAnchor={textAnchor}
      >
        {label}
      </text>
    </g>
  )
}
