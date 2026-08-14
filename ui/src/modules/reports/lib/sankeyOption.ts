import type { EChartsCoreOption } from 'echarts/core'
import { uiText } from '@shared/constants/uiText'
import { agentDisplayName, type FlowView } from './reportMath'

/**
 * Sankey node ids carry a column prefix so a source and an agent sharing a
 * display name can never collide; labels strip it back off.
 */
export const SOURCE_PREFIX = 'src:'
export const AGENT_PREFIX = 'agt:'
export const OUTCOME_PREFIX = 'out:'

/** CSS custom properties the sankey needs resolved (via useChartTheme). */
export const sankeyTokenNames = [
  '--color-brand',
  '--color-text',
  '--color-text-muted',
  '--color-surface',
  '--color-border',
  '--color-outcome-spoke-dot',
  '--color-outcome-attempted-dot',
  '--color-outcome-nothing-dot',
  '--color-outcome-spoke-ribbon',
  '--color-outcome-attempted-ribbon',
  '--color-outcome-nothing-ribbon',
  '--color-flow-ribbon',
] as const

export type SankeyTokens = Record<(typeof sankeyTokenNames)[number], string>

type SankeyNode = {
  name: string
  depth: number
  itemStyle: { color: string; borderWidth: number }
  label?: { position: 'left' }
}

type SankeyLink = {
  source: string
  target: string
  value: number
  lineStyle: { color: string; opacity: number }
}

const t = uiText.reports

/**
 * Entrance staging: the story pours in left to right — nodes land first, then
 * the source→agent ribbons, then the agent→outcome ribbons. Ribbons outside
 * the current stage sit at opacity 0 and ECharts' update transition fades
 * them in as the stage advances.
 */
export type RevealStage = 'nodes' | 'flow1' | 'full'

/**
 * Pure builder (RD-015): FlowView in, ECharts option out. The drill filter is
 * already applied by `flowView` — this only shapes what it is given.
 */
export function toSankeyOption(
  view: FlowView,
  showLabels: boolean,
  tokens: SankeyTokens,
  stage: RevealStage = 'full',
): EChartsCoreOption {
  const nodes: SankeyNode[] = []
  const links: SankeyLink[] = []

  for (const row of view.sources) {
    nodes.push({
      name: SOURCE_PREFIX + row.source,
      depth: 0,
      itemStyle: { color: tokens['--color-brand'], borderWidth: 0 },
      // handoff: source names sit to the LEFT of their bars, off the ribbons
      label: { position: 'left' },
    })
  }
  for (const agent of view.agents) {
    nodes.push({
      name: AGENT_PREFIX + String(agent.agentId),
      depth: 1,
      itemStyle: { color: tokens['--color-text-muted'], borderWidth: 0 },
    })
  }

  const outcomeMeta = [
    {
      key: 'spoke' as const,
      total: view.totals.spoke,
      dot: tokens['--color-outcome-spoke-dot'],
      ribbon: tokens['--color-outcome-spoke-ribbon'],
      label: t.outcomes.spoke,
    },
    {
      key: 'attempted' as const,
      total: view.totals.attempted,
      dot: tokens['--color-outcome-attempted-dot'],
      ribbon: tokens['--color-outcome-attempted-ribbon'],
      label: t.outcomes.attempted,
    },
    {
      key: 'nothing' as const,
      total: view.totals.nothing,
      dot: tokens['--color-outcome-nothing-dot'],
      ribbon: tokens['--color-outcome-nothing-ribbon'],
      label: t.outcomes.nothing,
    },
  ].filter((outcome) => outcome.total > 0)

  for (const outcome of outcomeMeta) {
    nodes.push({
      name: OUTCOME_PREFIX + outcome.key,
      depth: 2,
      itemStyle: { color: outcome.dot, borderWidth: 0 },
    })
  }

  const agentNameById = new Map(view.agents.map((agent) => [agent.agentId, agentDisplayName(agent)]))

  const flow1Opacity = stage === 'nodes' ? 0 : 1
  const flow2Opacity = stage === 'full' ? 1 : 0

  for (const row of view.sources) {
    for (const agent of row.agents) {
      if (agent.counts.leads === 0) continue
      links.push({
        source: SOURCE_PREFIX + row.source,
        target: AGENT_PREFIX + String(agent.agentId),
        value: agent.counts.leads,
        lineStyle: { color: tokens['--color-flow-ribbon'], opacity: flow1Opacity },
      })
    }
  }
  for (const agent of view.agents) {
    for (const outcome of outcomeMeta) {
      const value = agent.counts[outcome.key]
      if (value === 0) continue
      links.push({
        source: AGENT_PREFIX + String(agent.agentId),
        target: OUTCOME_PREFIX + outcome.key,
        value,
        lineStyle: { color: outcome.ribbon, opacity: flow2Opacity },
      })
    }
  }

  const displayName = (nodeId: string): string => {
    if (nodeId.startsWith(SOURCE_PREFIX)) return nodeId.slice(SOURCE_PREFIX.length)
    if (nodeId.startsWith(AGENT_PREFIX)) {
      const agentId = Number(nodeId.slice(AGENT_PREFIX.length))
      return agentNameById.get(agentId) ?? nodeId
    }
    const key = nodeId.slice(OUTCOME_PREFIX.length) as (typeof outcomeMeta)[number]['key']
    return t.outcomeShort[key]
  }

  return {
    // initial render: nodes settle fast; stage advances fade ribbons in
    animationDuration: 300,
    animationDurationUpdate: 420,
    animationEasingUpdate: 'cubicOut',
    tooltip: {
      trigger: 'item',
      backgroundColor: tokens['--color-surface'],
      borderColor: tokens['--color-border'],
      textStyle: { color: tokens['--color-text'], fontSize: 12.5 },
      formatter: (params: unknown) => {
        const p = params as {
          dataType: 'node' | 'edge'
          name: string
          value: number
          data: { source?: string; target?: string }
        }
        if (p.dataType === 'edge') {
          return t.flow.ribbonTooltip(
            displayName(p.data.source ?? ''),
            displayName(p.data.target ?? ''),
            p.value,
          )
        }
        return `${displayName(p.name)} · ${p.value}`
      },
    },
    series: [
      {
        type: 'sankey',
        data: nodes,
        links,
        nodeWidth: 12,
        nodeGap: 14,
        nodeAlign: 'justify',
        layoutIterations: 0,
        emphasis: { focus: 'adjacency' },
        // margins reserve room for the outboard labels; collapsing them when
        // labels are off morphs the diagram wider (merge mode animates it)
        left: showLabels ? 150 : 12,
        top: 44,
        right: showLabels ? 140 : 12,
        bottom: 12,
        label: {
          show: showLabels,
          color: tokens['--color-text'],
          fontSize: 13,
          fontWeight: 700,
          formatter: (params: unknown) => {
            const p = params as { name: string; value: number }
            return `${displayName(p.name)}  {muted|${p.value}}`
          },
          rich: {
            muted: {
              color: tokens['--color-text-muted'],
              fontSize: 12,
              fontWeight: 600,
            },
          },
        },
        lineStyle: { curveness: 0.5 },
      },
    ],
  }
}
