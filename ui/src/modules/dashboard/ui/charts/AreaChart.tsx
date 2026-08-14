import { useId } from 'react'

type Point = { x: number; y: number }

type AreaChartProps = {
  data: number[]
  height?: number
  color?: string
  pad?: number
  strokeWidth?: number
}

// Smooth area + line with a faint baseline and a hollow end-point marker. Token-driven SVG, no
// chart lib (RD-013). Ported from the design handoff's charts.jsx.
export function AreaChart({
  data,
  height = 150,
  color = 'var(--color-brand)',
  pad = 8,
  strokeWidth = 2.4,
}: AreaChartProps) {
  const gradientId = `area-${useId().replace(/:/g, '')}`
  const width = 640

  if (data.length < 2) {
    return <svg viewBox={`0 0 ${width} ${height}`} width="100%" height={height} preserveAspectRatio="none" />
  }

  const max = Math.max(...data) * 1.12
  const min = Math.min(...data) * 0.86
  const span = max - min || 1
  const innerW = width - pad * 2
  const innerH = height - pad * 2
  const points: Point[] = data.map((value, index) => ({
    x: pad + (index / (data.length - 1)) * innerW,
    y: pad + innerH - ((value - min) / span) * innerH,
  }))
  const line = smoothPath(points)
  const last = points[points.length - 1]
  const area = `${line} L ${last.x} ${pad + innerH} L ${points[0].x} ${pad + innerH} Z`

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width="100%"
      height={height}
      preserveAspectRatio="none"
      style={{ display: 'block', overflow: 'visible' }}
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.22" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <line x1={pad} y1={pad + innerH} x2={width - pad} y2={pad + innerH} stroke="var(--color-border)" strokeWidth="1" />
      <path d={area} fill={`url(#${gradientId})`} />
      <path
        d={line}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
      <circle cx={last.x} cy={last.y} r="5" fill="var(--color-surface)" stroke={color} strokeWidth="2.4" />
    </svg>
  )
}

// Catmull-Rom → cubic bezier, for a smooth (not wobbly) line through the points.
function smoothPath(points: Point[]): string {
  if (points.length < 2) return ''
  let d = `M ${points[0].x} ${points[0].y}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] ?? points[i]
    const p1 = points[i]
    const p2 = points[i + 1]
    const p3 = points[i + 2] ?? p2
    const c1x = p1.x + (p2.x - p0.x) / 6
    const c1y = p1.y + (p2.y - p0.y) / 6
    const c2x = p2.x - (p3.x - p1.x) / 6
    const c2y = p2.y - (p3.y - p1.y) / 6
    d += ` C ${c1x} ${c1y} ${c2x} ${c2y} ${p2.x} ${p2.y}`
  }
  return d
}
