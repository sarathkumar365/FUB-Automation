type SparkbarsProps = {
  data: number[]
  height?: number
  color?: string
  highlight?: number
  gap?: number
}

// Compact vertical bars; the last `highlight` bars are full-color, the rest faded. Ported from the
// design handoff's charts.jsx.
export function Sparkbars({ data, height = 26, color = 'var(--color-brand)', highlight = 2, gap = 3 }: SparkbarsProps) {
  if (data.length === 0) {
    return null
  }
  const max = Math.max(...data) || 1
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap, height }}>
      {data.map((value, index) => (
        <span
          key={index}
          style={{
            flex: 1,
            minHeight: 2,
            height: `${(value / max) * 100}%`,
            borderRadius: 2,
            background:
              index >= data.length - highlight ? color : `color-mix(in srgb, ${color} 30%, transparent)`,
          }}
        />
      ))}
    </div>
  )
}
