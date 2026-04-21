/**
 * A labeled bezier edge between two scenes in the vertical storyboard.
 *
 * Vertical flow: starts at the bottom-center of the parent scene and lands
 * at the top-center of the child. The label is the result code produced by
 * the source step — "why did we go this way?". When multiple edges leave
 * the same scene (branching), each label is placed at a distinct fraction
 * along the bezier (see `edgeLabelT`) so the chips never overlap.
 */
import type { SceneLayout } from '../../model/layoutEngine'
import { CHIP_FONT_SIZE, estimateChipWidth } from './chipMetrics'
import { cubicBezierPoint, edgeLabelT } from './edgeMath'

export interface ExitEdgeProps {
  id: string
  from: SceneLayout
  to: SceneLayout
  resultCode: string
  /** Position of this exit among siblings sharing the same `from` scene. */
  edgeIndex: number
  /** Total siblings sharing the same `from` scene. */
  edgeCount: number
}

export function ExitEdge({ id, from, to, resultCode, edgeIndex, edgeCount }: ExitEdgeProps) {
  const startX = from.x
  const startY = from.y + from.height / 2
  const endX = to.x
  const endY = to.y - to.height / 2
  const midY = (startY + endY) / 2
  const p0 = { x: startX, y: startY }
  const p1 = { x: startX, y: midY }
  const p2 = { x: endX, y: midY }
  const p3 = { x: endX, y: endY }
  const path = `M ${p0.x} ${p0.y} C ${p1.x} ${p1.y}, ${p2.x} ${p2.y}, ${p3.x} ${p3.y}`
  const t = edgeLabelT(edgeIndex, edgeCount)
  const labelPoint = cubicBezierPoint(t, p0, p1, p2, p3)
  const rectWidth = estimateChipWidth(resultCode, { minWidth: 48 })
  const rectHeight = 20

  return (
    <g
      data-builder-region="exit-edge"
      data-edge-id={id}
      data-result-code={resultCode}
      data-chip-width={rectWidth}
    >
      <path
        d={path}
        fill="none"
        stroke="var(--color-storyboard-edge)"
        strokeWidth={1.5}
        markerEnd="url(#storyboard-arrow)"
      />
      <g transform={`translate(${labelPoint.x}, ${labelPoint.y})`}>
        <rect
          x={-rectWidth / 2}
          y={-rectHeight / 2}
          rx={8}
          ry={8}
          width={rectWidth}
          height={rectHeight}
          fill="var(--color-surface)"
          stroke="var(--color-storyboard-edge-soft)"
        />
        <text
          x={0}
          y={4}
          textAnchor="middle"
          fontSize={CHIP_FONT_SIZE}
          fontFamily="var(--font-mono)"
          fill="var(--color-storyboard-chip-strong-text)"
        >
          {resultCode}
        </text>
      </g>
    </g>
  )
}
