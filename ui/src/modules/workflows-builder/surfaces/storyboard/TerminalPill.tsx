/**
 * Terminal markers — rendered as small pills hanging off a scene's right
 * edge, stacked vertically. Each pill represents a resultCode that maps to
 * `{ terminal: reason }` in the graph: "workflow ends here" outcomes without
 * adding extra scene nodes to the canvas.
 */
import type { SceneLayout } from '../../model/layoutEngine'

export interface TerminalPillProps {
  id: string
  from: SceneLayout
  resultCode: string
  reason: string
  /** Zero-based position among terminals for the same `from` scene. */
  index: number
  /** Total terminals attached to the same `from` scene (for centering). */
  totalTerminals: number
}

export function TerminalPill({
  id,
  from,
  resultCode,
  reason,
  index,
  totalTerminals,
}: TerminalPillProps) {
  const anchorX = from.x + from.width / 2
  const anchorY = from.y
  const pillX = anchorX + 28
  const rowSpacing = 26
  const pillY = anchorY + (index - (totalTerminals - 1) / 2) * rowSpacing
  const label = `${resultCode} → ${reason}`
  const width = Math.max(140, label.length * 6 + 20)

  return (
    <g data-builder-region="terminal-pill" data-terminal-id={id}>
      <line
        x1={anchorX}
        y1={anchorY}
        x2={pillX}
        y2={pillY}
        stroke="rgba(15, 23, 42, 0.2)"
        strokeWidth={1.25}
        strokeDasharray="3 3"
      />
      <rect
        x={pillX}
        y={pillY - 11}
        width={width}
        height={22}
        rx={11}
        ry={11}
        fill="rgba(100, 116, 139, 0.08)"
        stroke="rgba(100, 116, 139, 0.35)"
      />
      <text
        x={pillX + 10}
        y={pillY + 4}
        fontSize={11}
        fontFamily="JetBrains Mono, monospace"
        fill="#475569"
      >
        {label}
      </text>
    </g>
  )
}
