import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { SankeyChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'

/**
 * The one place ECharts modules are registered (RD-015). Only what is listed
 * here ships in the bundle — when a feature needs a new chart type, add its
 * chart/component import here rather than importing the full `echarts` entry.
 */
echarts.use([CanvasRenderer, SankeyChart, TooltipComponent])

export { echarts }
