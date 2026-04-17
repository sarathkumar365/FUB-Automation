/**
 * Terminal markers — rendered as small pills hanging off a scene's right edge
 * for each resultCode that maps to `{ terminal: reason }` in the graph.
 * Shows the "workflow ends here" outcomes without needing extra scene nodes.
 */
import type { SceneLayout } from '../../model/layoutEngine'

export interface TerminalPillProps {
  id: string
  from: SceneLayout
  resultCode: string
  reason: string
  index: number
}

export function TerminalPill({ id, from, resultCode, reason, index }: TerminalPillProps) {
  const anchorX = from.x + from.width / 2
  const anchorY = from.y
  const offsetY = index * 26 - ((index > 0 ? 1 : 0) * 8)
  const pillX = anchorX + 36
  const pillY = anchorY + offsetY
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
