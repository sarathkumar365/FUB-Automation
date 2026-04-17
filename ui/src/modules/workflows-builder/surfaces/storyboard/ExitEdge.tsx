/**
 * A labeled bezier edge between two scenes. The label is the result code
 * produced by the source step — this is the "why did we go this way?" piece
 * the debug inspector will highlight during Trace view in Phase 4.
 */
import type { SceneLayout } from '../../model/layoutEngine'

export interface ExitEdgeProps {
  id: string
  from: SceneLayout
  to: SceneLayout
  resultCode: string
}

export function ExitEdge({ id, from, to, resultCode }: ExitEdgeProps) {
  const startX = from.x + from.width / 2
  const startY = from.y
  const endX = to.x - to.width / 2
  const endY = to.y
  const midX = (startX + endX) / 2
  const labelX = midX
  const labelY = (startY + endY) / 2 - 8
  const path = `M ${startX} ${startY} C ${midX} ${startY}, ${midX} ${endY}, ${endX} ${endY}`

  return (
    <g data-builder-region="exit-edge" data-edge-id={id} data-result-code={resultCode}>
      <path
        d={path}
        fill="none"
        stroke="rgba(15, 23, 42, 0.28)"
        strokeWidth={1.5}
        markerEnd="url(#storyboard-arrow)"
      />
      <g transform={`translate(${labelX}, ${labelY})`}>
        <rect
          x={-(resultCode.length * 3.5 + 10)}
          y={-10}
          rx={8}
          ry={8}
          width={resultCode.length * 7 + 20}
          height={20}
          fill="#ffffff"
          stroke="rgba(15, 23, 42, 0.18)"
        />
        <text
          x={0}
          y={4}
          textAnchor="middle"
          fontSize={11}
          fontFamily="JetBrains Mono, monospace"
          fill="#334155"
        >
          {resultCode}
        </text>
      </g>
    </g>
  )
}
