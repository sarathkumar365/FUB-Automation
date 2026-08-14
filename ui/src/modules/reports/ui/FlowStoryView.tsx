import { useEffect, useMemo, useState } from 'react'
import type { SourceContactReport } from '@platform/contracts/reportingSchemas'
import { EChart, type EChartEventHandler } from '@platform/charts/EChart'
import { useChartTheme } from '@platform/charts/chartTheme'
import { uiText } from '@shared/constants/uiText'
import { EyeIcon } from '@shared/ui'
import { cn } from '@shared/lib/cn'
import {
  agentDisplayName,
  flowView,
  type FlowDrill,
} from '../lib/reportMath'
import {
  AGENT_PREFIX,
  OUTCOME_PREFIX,
  SOURCE_PREFIX,
  sankeyTokenNames,
  toSankeyOption,
  type RevealStage,
} from '../lib/sankeyOption'
import { OutcomeDot } from './ReportBits'

const t = uiText.reports

type FlowStoryViewProps = {
  report: SourceContactReport
  drill: FlowDrill
  onDrillChange: (drill: FlowDrill) => void
  showLabels: boolean
  onToggleLabels: () => void
  windowPhrase: string
}

export function FlowStoryView({
  report,
  drill,
  onDrillChange,
  showLabels,
  onToggleLabels,
  windowPhrase,
}: FlowStoryViewProps) {
  const tokens = useChartTheme(sankeyTokenNames)
  const view = useMemo(() => flowView(report, drill), [report, drill])
  const stage = useRevealStages()
  const option = useMemo(
    () => toSankeyOption(view, showLabels, tokens, stage),
    [view, showLabels, tokens, stage],
  )

  const onEvents = useMemo<Record<string, EChartEventHandler>>(
    () => ({
      click: (params) => {
        const p = params as { dataType?: string; name?: string }
        if (p.dataType !== 'node' || !p.name) return
        if (p.name.startsWith(SOURCE_PREFIX)) {
          const source = p.name.slice(SOURCE_PREFIX.length)
          onDrillChange({ source: drill.source === source ? null : source, agentId: null })
        } else if (p.name.startsWith(AGENT_PREFIX)) {
          const agentId = Number(p.name.slice(AGENT_PREFIX.length))
          onDrillChange({ ...drill, agentId: drill.agentId === agentId ? null : agentId })
        } else if (p.name.startsWith(OUTCOME_PREFIX)) {
          // outcomes are terminal — no drill
        }
      },
    }),
    [drill, onDrillChange],
  )

  const drillLine = buildDrillLine(report, drill, windowPhrase)
  const crumbs = buildCrumbs(report, drill, onDrillChange)

  return (
    <div>
      <div className="mt-4 flex flex-wrap items-center gap-4">
        <nav aria-label={t.flow.breadcrumbAriaLabel} className="flex flex-wrap items-center gap-2 text-[13.5px]">
          {crumbs.map((crumb, index) => (
            // crumbs are positional (all → source → agent); depth is the identity
            <span key={index} className="flex items-center gap-2">
              {index > 0 ? <span className="text-[var(--color-text-muted)]">→</span> : null}
              {crumb.onClick ? (
                <button
                  type="button"
                  onClick={crumb.onClick}
                  className="cursor-pointer border-b border-dashed border-[var(--color-brand)] font-semibold text-[var(--color-brand)]"
                >
                  {crumb.label}
                </button>
              ) : (
                <span className="font-extrabold text-[var(--color-text)]">{crumb.label}</span>
              )}
            </span>
          ))}
        </nav>
        <button
          type="button"
          aria-pressed={showLabels}
          onClick={onToggleLabels}
          className={cn(
            'ml-auto inline-flex items-center gap-1.5 rounded-lg border border-[var(--color-border)] px-2.5 py-1.5 text-[12.5px] font-bold',
            showLabels
              ? 'bg-[var(--color-brand-soft)] text-[var(--color-brand)]'
              : 'bg-transparent text-[var(--color-text-muted)]',
          )}
        >
          <EyeIcon className="h-[15px] w-[15px]" />
          {showLabels ? t.flow.labelsOn : t.flow.labelsOff}
        </button>
      </div>

      <section className="mt-3.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-5 pb-3.5 shadow-[var(--shadow-subtle)]">
        {drillLine ? (
          <p className="mb-3 max-w-[820px] text-[15px] leading-normal text-[var(--color-text)]">{drillLine}</p>
        ) : null}
        <div className="relative w-full">
          <div className="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-between px-1">
            <ColumnHead kicker={t.flow.columnSources} sub={t.flow.columnSourcesSub} />
            <ColumnHead kicker={t.flow.columnAgents} sub={t.flow.columnAgentsSub} align="center" />
            <ColumnHead kicker={t.flow.columnOutcome} sub={t.flow.columnOutcomeSub} align="right" />
          </div>
          <EChart
            option={option}
            onEvents={onEvents}
            ariaLabel={t.flow.chartAriaLabel}
            className="aspect-[1080/720] max-h-[640px] min-h-[360px] w-full min-w-[400px]"
          />
        </div>
        <div className="mt-1.5 flex flex-wrap items-center justify-between gap-4 border-t border-[var(--color-border)] pt-3">
          <div className="flex flex-wrap gap-4">
            <LegendEntry outcome="spoke" label={t.outcomes.spoke} />
            <LegendEntry outcome="attempted" label={t.outcomes.attempted} />
            <LegendEntry outcome="nothing" label={t.outcomes.nothing} />
          </div>
          <p className="text-[12.5px] text-[var(--color-text-muted)]">{t.flow.legendHint}</p>
        </div>
      </section>
    </div>
  )
}

/**
 * Staged story reveal: nodes land, then source→agent ribbons, then
 * agent→outcome ribbons. Runs once per mount — the page remounts this view
 * per report/window switch, so drill clicks never retrigger it. Reduced-motion
 * users get the finished chart straight away.
 */
function useRevealStages(): RevealStage {
  const [stage, setStage] = useState<RevealStage>(() =>
    window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ? 'full' : 'nodes',
  )

  useEffect(() => {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches) return
    const ribbons = setTimeout(() => setStage('flow1'), 280)
    const outcomes = setTimeout(() => setStage('full'), 760)
    return () => {
      clearTimeout(ribbons)
      clearTimeout(outcomes)
    }
  }, [])

  return stage
}

function ColumnHead({
  kicker,
  sub,
  align = 'left',
}: {
  kicker: string
  sub: string
  align?: 'left' | 'center' | 'right'
}) {
  return (
    <div className={cn(align === 'center' && 'text-center', align === 'right' && 'text-right')}>
      <span className="block whitespace-nowrap text-xs font-extrabold tracking-[0.08em] text-[var(--color-text)]">
        {kicker}
      </span>
      <span className="mt-0.5 block whitespace-nowrap text-xs text-[var(--color-text-muted)]">{sub}</span>
    </div>
  )
}

function LegendEntry({ outcome, label }: { outcome: 'spoke' | 'attempted' | 'nothing'; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-[12.5px] text-[var(--color-text-muted)]">
      <OutcomeDot outcome={outcome} />
      {label}
    </span>
  )
}

type Crumb = { label: string; onClick: (() => void) | null }

function buildCrumbs(
  report: SourceContactReport,
  drill: FlowDrill,
  onDrillChange: (drill: FlowDrill) => void,
): Crumb[] {
  const hasDrill = drill.source !== null || drill.agentId !== null
  const crumbs: Crumb[] = [
    {
      label: t.flow.allLeadsCrumb(report.totals.leads),
      onClick: hasDrill ? () => onDrillChange({ source: null, agentId: null }) : null,
    },
  ]
  if (drill.source !== null) {
    const row = report.sources.find((source) => source.source === drill.source)
    crumbs.push({
      label: t.flow.crumb(drill.source, row?.counts.leads ?? 0),
      onClick: drill.agentId !== null ? () => onDrillChange({ ...drill, agentId: null }) : null,
    })
  }
  if (drill.agentId !== null) {
    const view = flowView(report, drill)
    const agent = view.agents.find((candidate) => candidate.agentId === drill.agentId)
    if (agent) {
      crumbs.push({
        label: t.flow.crumb(agentDisplayName(agent), agent.counts.leads),
        onClick: null,
      })
    }
  }
  return crumbs
}

function buildDrillLine(
  report: SourceContactReport,
  drill: FlowDrill,
  windowPhrase: string,
): string {
  if (drill.source === null && drill.agentId === null) return ''
  const view = flowView(report, drill)
  const totals = view.totals
  const outcomes = t.flow.drillOutcomes(totals.spoke, totals.attempted, totals.nothing)
  const leads = t.narration.leadCount(totals.leads)

  if (drill.source !== null && drill.agentId === null) {
    const top = view.agents[0]
    return (
      t.flow.drillSource(drill.source, leads, windowPhrase) +
      (top ? t.flow.drillTopHolder(agentDisplayName(top), top.counts.leads) : '') +
      outcomes
    )
  }
  const agent = view.agents.find((candidate) => candidate.agentId === drill.agentId)
  if (!agent) return ''
  if (drill.source === null) {
    return (
      t.flow.drillAgent(
        agentDisplayName(agent),
        leads,
        t.narration.sourceCount(view.sources.length),
      ) + outcomes
    )
  }
  return t.flow.drillBoth(drill.source, agentDisplayName(agent), leads) + outcomes
}
