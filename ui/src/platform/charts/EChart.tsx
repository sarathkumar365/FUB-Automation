import { useEffect, useRef } from 'react'
import type { EChartsCoreOption, EChartsType } from 'echarts/core'
import { echarts } from './echartsSetup'

export type EChartEventHandler = (params: unknown) => void

type EChartProps = {
  option: EChartsCoreOption
  /** ECharts event name → handler, e.g. `{ click: onNodeClick }`. */
  onEvents?: Record<string, EChartEventHandler>
  ariaLabel?: string
  className?: string
}

/**
 * The single ECharts host (RD-015): every chart in the app renders through
 * this. It owns init, resize, event binding, and disposal; it never shapes
 * data — options arrive fully built from a pure builder function.
 */
export function EChart({ option, onEvents, ariaLabel, className }: EChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const chartRef = useRef<EChartsType | null>(null)
  const eventsRef = useRef<Record<string, EChartEventHandler> | undefined>(undefined)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    // jsdom has no canvas — render the (labelled) host div and skip the chart,
    // so tests can mount chart pages without mocking this component.
    if (!container.ownerDocument.createElement('canvas').getContext('2d')) return
    const chart = echarts.init(container)
    chartRef.current = chart
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(container)
    return () => {
      observer.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    // Merge mode so successive options diff-animate (ECharts' update
    // transition) — notMerge would rebuild the series and make every
    // change snap instead of ease.
    chartRef.current?.setOption(option)
  }, [option])

  useEffect(() => {
    const chart = chartRef.current
    if (!chart) return
    for (const name of Object.keys(eventsRef.current ?? {})) chart.off(name)
    for (const [name, handler] of Object.entries(onEvents ?? {})) chart.on(name, handler)
    eventsRef.current = onEvents
  }, [onEvents])

  return <div ref={containerRef} role="img" aria-label={ariaLabel} className={className} />
}
