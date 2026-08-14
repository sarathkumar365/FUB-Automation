import { describe, expect, it } from 'vitest'
import { flowView } from '@modules/reports/lib/reportMath'
import {
  AGENT_PREFIX,
  OUTCOME_PREFIX,
  SOURCE_PREFIX,
  sankeyTokenNames,
  toSankeyOption,
  type SankeyTokens,
} from '@modules/reports/lib/sankeyOption'
import { sampleReport } from './reportingFixtures'

const tokens = Object.fromEntries(sankeyTokenNames.map((name) => [name, `resolved(${name})`])) as SankeyTokens

type SankeySeries = {
  data: { name: string; depth: number; itemStyle: { color: string } }[]
  links: {
    source: string
    target: string
    value: number
    lineStyle: { color: string; opacity: number }
  }[]
  label: { show: boolean }
}

function seriesOf(option: ReturnType<typeof toSankeyOption>): SankeySeries {
  return (option as { series: SankeySeries[] }).series[0]
}

describe('sankey option builder', () => {
  const view = flowView(sampleReport(), { source: null, agentId: null })

  it('builds three prefixed columns so name collisions are impossible', () => {
    const series = seriesOf(toSankeyOption(view, true, tokens))

    const depths = new Map(series.data.map((node) => [node.name, node.depth]))
    expect(depths.get(`${SOURCE_PREFIX}Facebook`)).toBe(0)
    expect(depths.get(`${AGENT_PREFIX}1`)).toBe(1)
    expect(depths.get(`${OUTCOME_PREFIX}spoke`)).toBe(2)
  })

  it('link volumes reconcile with the report totals', () => {
    const series = seriesOf(toSankeyOption(view, true, tokens))

    const intoAgents = series.links
      .filter((link) => link.source.startsWith(SOURCE_PREFIX))
      .reduce((sum, link) => sum + link.value, 0)
    const intoOutcomes = series.links
      .filter((link) => link.target.startsWith(OUTCOME_PREFIX))
      .reduce((sum, link) => sum + link.value, 0)

    expect(intoAgents).toBe(25)
    expect(intoOutcomes).toBe(25)
  })

  it('every color comes from a resolved design token, never a literal', () => {
    const series = seriesOf(toSankeyOption(view, true, tokens))

    for (const node of series.data) {
      expect(node.itemStyle.color).toMatch(/^resolved\(--color-/)
    }
    for (const link of series.links) {
      expect(link.lineStyle.color).toMatch(/^resolved\(--color-/)
    }
  })

  it('outcome ribbons carry their outcome color, source ribbons the flow color', () => {
    const series = seriesOf(toSankeyOption(view, true, tokens))

    const sourceRibbon = series.links.find((link) => link.source.startsWith(SOURCE_PREFIX))
    const spokeRibbon = series.links.find((link) => link.target === `${OUTCOME_PREFIX}spoke`)
    expect(sourceRibbon?.lineStyle.color).toBe('resolved(--color-flow-ribbon)')
    expect(spokeRibbon?.lineStyle.color).toBe('resolved(--color-outcome-spoke-ribbon)')
  })

  it('reveal stages fade ribbons in column by column', () => {
    const isSourceRibbon = (link: { source: string }) => link.source.startsWith(SOURCE_PREFIX)
    const opacities = (stage: 'nodes' | 'flow1' | 'full') => {
      const series = seriesOf(toSankeyOption(view, true, tokens, stage))
      return {
        flow1: series.links.filter(isSourceRibbon).map((link) => link.lineStyle.opacity),
        flow2: series.links.filter((link) => !isSourceRibbon(link)).map((link) => link.lineStyle.opacity),
      }
    }

    expect(opacities('nodes')).toEqual({ flow1: [0, 0, 0], flow2: expect.arrayContaining([0]) })
    expect(new Set(opacities('flow1').flow1)).toEqual(new Set([1]))
    expect(new Set(opacities('flow1').flow2)).toEqual(new Set([0]))
    expect(new Set(opacities('full').flow2)).toEqual(new Set([1]))
  })

  it('the labels toggle only flips label visibility', () => {
    expect(seriesOf(toSankeyOption(view, true, tokens)).label.show).toBe(true)
    expect(seriesOf(toSankeyOption(view, false, tokens)).label.show).toBe(false)
  })

  it('an outcome nobody reached is omitted rather than drawn as an empty bar', () => {
    const drilled = flowView(sampleReport(), { source: 'Instagram', agentId: null })
    const noSpoke = {
      ...drilled,
      totals: { ...drilled.totals, spoke: 0 },
    }
    const series = seriesOf(toSankeyOption(noSpoke, true, tokens))

    expect(series.data.some((node) => node.name === `${OUTCOME_PREFIX}spoke`)).toBe(false)
  })
})
