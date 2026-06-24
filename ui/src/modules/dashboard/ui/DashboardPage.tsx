import { useMemo, type CSSProperties } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useShellRegionRegistration } from '@app/useShellRegionRegistration'
import { routes } from '@shared/constants/routes'
import { uiText } from '@shared/constants/uiText'
import { formatDateTime } from '@shared/lib/date'
import { ErrorState } from '@shared/ui/ErrorState'
import { LoadingState } from '@shared/ui/LoadingState'
import { PageCard } from '@shared/ui/PageCard'
import { PageHeader } from '@shared/ui/PageHeader'
import { StatusBadge } from '@shared/ui/StatusBadge'
import { formatWorkflowRunStatus, getWorkflowRunStatusTone } from '@modules/workflow-runs/lib/workflowRunsDisplay'
import type {
  DashboardFailureRow,
  DashboardRunRow,
  Delta,
} from '@platform/contracts/dashboardSchemas'
import { useDashboardSnapshotQuery } from '../data/useDashboardSnapshotQuery'
import './DashboardPage.css'

type StatTone = 'default' | 'error' | 'ok'

export function DashboardPage() {
  const navigate = useNavigate()
  const snapshotQuery = useDashboardSnapshotQuery()
  const snapshot = snapshotQuery.data

  const panelRegion = useMemo(
    () => ({
      title: uiText.dashboard.panelTitle,
      body: (
        <div className="space-y-2 text-sm">
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.runsTitle}: </span>
            {snapshot?.hero.stats.runs.value ?? 0}
          </p>
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.openFailuresTitle}: </span>
            {snapshot?.hero.openFailures ?? 0}
          </p>
        </div>
      ),
    }),
    [snapshot?.hero.stats.runs.value, snapshot?.hero.openFailures],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.dashboard.inspectorTitle,
      body: <p className="text-sm text-[var(--color-text-muted)]">{uiText.dashboard.inspectorDescription}</p>,
    }),
    [],
  )

  useShellRegionRegistration({ panel: panelRegion, inspector: inspectorRegion })

  if (snapshotQuery.isPending) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.dashboard.title} subtitle={uiText.dashboard.subtitle} />
        <LoadingState />
      </div>
    )
  }

  if (snapshotQuery.isError || !snapshot) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.dashboard.title} subtitle={uiText.dashboard.subtitle} />
        <ErrorState onRetry={() => void snapshotQuery.refetch()} />
      </div>
    )
  }

  const { hero, funnel, throughput, recentRuns, needsAttention } = snapshot
  const successRateValue =
    hero.stats.successRate.value === null
      ? uiText.workflowRuns.missingValue
      : `${hero.stats.successRate.value.toFixed(1)}%`

  return (
    <div className="dash-root space-y-4">
      <div className="dash-blob dash-blob-tr" aria-hidden="true" />
      <div className="dash-blob dash-blob-bl" aria-hidden="true" />

      <PageHeader title={uiText.dashboard.health[hero.state]} subtitle={uiText.dashboard.subtitle} />
      <p className="dash-item text-xs text-[var(--color-text-muted)]">
        {uiText.dashboard.updatedLabel} {formatDateTime(snapshot.window.to)} · {throughput.perMin}{' '}
        {uiText.dashboard.perMinLabel}
      </p>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile label={uiText.dashboard.runsTitle} value={hero.stats.runs.value} delta={hero.stats.runs.delta} tone="default" delay={0} />
        <StatTile label={uiText.dashboard.successRateTitle} value={successRateValue} delta={hero.stats.successRate.delta} tone="default" delay={60} />
        <StatTile
          label={uiText.dashboard.openFailuresTitle}
          value={hero.openFailures}
          delta={hero.stats.openFailures.delta}
          tone={hero.openFailures > 0 ? 'error' : 'ok'}
          delay={120}
        />
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <FunnelTile label={uiText.dashboard.funnelIngested} value={funnel.ingested.value} />
        <FunnelTile label={uiText.dashboard.funnelDomainEvents} value={funnel.domainEvents.value} />
        <FunnelTile label={uiText.dashboard.funnelRuns} value={funnel.runs.value} />
        <FunnelTile label={uiText.dashboard.funnelFailed} value={funnel.failed.value} tone={funnel.failed.value > 0 ? 'error' : 'ok'} />
      </div>

      <div className="dash-item" style={{ ['--delay' as string]: '180ms' } as CSSProperties}>
        <PageCard title={uiText.dashboard.recentRunsTitle}>
          {recentRuns.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">{uiText.dashboard.recentRunsEmpty}</p>
          ) : (
            <RunList runs={recentRuns} onSelect={(run) => navigate(routes.workflowRunDetail(run.id))} />
          )}
          <Link
            to={routes.workflowRuns}
            className="mt-3 inline-flex h-8 items-center justify-center rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-xs font-medium text-[var(--color-text)] transition-colors hover:bg-[var(--color-surface-alt)]"
          >
            {uiText.dashboard.openRuns}
          </Link>
        </PageCard>
      </div>

      <div className="dash-item" style={{ ['--delay' as string]: '240ms' } as CSSProperties}>
        <PageCard title={uiText.dashboard.needsAttentionTitle}>
          {needsAttention.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">{uiText.dashboard.needsAttentionEmpty}</p>
          ) : (
            <AttentionList rows={needsAttention} onSelect={(row) => navigate(routes.workflowRunDetail(Number(row.ref)))} />
          )}
        </PageCard>
      </div>
    </div>
  )
}

type StatTileProps = {
  label: string
  value: number | string
  delta: Delta
  tone: StatTone
  delay: number
}

function StatTile({ label, value, delta, tone, delay }: StatTileProps) {
  const accentColor =
    tone === 'error' ? 'var(--color-status-bad)' : tone === 'ok' ? 'var(--color-status-ok)' : 'var(--color-brand)'

  return (
    <div
      className="stat-tile dash-item flex flex-col rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-subtle)]"
      style={{ borderLeftColor: accentColor, borderLeftWidth: '3px', ['--delay' as string]: `${delay}ms` } as CSSProperties}
    >
      <h3 className="text-xs font-medium uppercase tracking-wide text-[var(--color-text-muted)]">{label}</h3>
      <span className="mt-2 text-3xl font-semibold leading-none text-[var(--color-text)]">{value}</span>
      <span className="mt-1 text-xs">
        <DeltaLabel delta={delta} />
      </span>
    </div>
  )
}

function FunnelTile({ label, value, tone = 'default' }: { label: string; value: number; tone?: StatTone }) {
  const dotColor = tone === 'error' ? 'var(--color-status-bad)' : 'var(--color-brand)'
  return (
    <div className="dash-item flex flex-col rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-[var(--shadow-subtle)]">
      <span className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-[var(--color-text-muted)]">
        <span className="inline-block h-[7px] w-[7px] rounded-full" style={{ backgroundColor: dotColor }} aria-hidden="true" />
        {label}
      </span>
      <span className="mt-2 font-mono text-2xl font-semibold text-[var(--color-text)]">{value}</span>
    </div>
  )
}

function DeltaLabel({ delta }: { delta: Delta }) {
  if (delta.value === null) {
    return <span className="text-[var(--color-text-muted)]">{uiText.workflowRuns.missingValue}</span>
  }
  const arrow = delta.direction === 'UP' ? '▲' : delta.direction === 'DOWN' ? '▼' : '—'
  return (
    <span className="text-[var(--color-text-muted)]">
      {arrow} {Math.abs(delta.value).toFixed(1)}
    </span>
  )
}

function RunList({ runs, onSelect }: { runs: DashboardRunRow[]; onSelect: (run: DashboardRunRow) => void }) {
  return (
    <div className="overflow-x-auto rounded-md border border-[var(--color-border)]">
      <table className="min-w-full text-left text-xs">
        <thead className="bg-[var(--color-surface-alt)] text-[var(--color-text-muted)]">
          <tr>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.runIdHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.workflowKeyHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.statusHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.durationHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.completedAtHeader}</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr
              key={run.id}
              role="button"
              tabIndex={0}
              aria-label={`${uiText.dashboard.runRowAriaLabelPrefix} ${run.id}`}
              onClick={() => onSelect(run)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  onSelect(run)
                }
              }}
              className="cursor-pointer border-t border-[var(--color-border)] transition-colors hover:bg-[var(--color-surface-alt)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)] ring-offset-[var(--color-surface)]"
            >
              <td className="px-2 py-2 font-mono">{run.id}</td>
              <td className="px-2 py-2 font-mono">{run.workflowKey}</td>
              <td className="px-2 py-2">
                <StatusBadge
                  label={formatWorkflowRunStatus(run.status)}
                  tone={getWorkflowRunStatusTone(run.status)}
                />
              </td>
              <td className="px-2 py-2 font-mono">{run.durationSec === null ? uiText.workflowRuns.missingValue : `${run.durationSec}s`}</td>
              <td className="px-2 py-2">{run.completedAt === null ? uiText.workflowRuns.missingValue : formatDateTime(run.completedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function AttentionList({ rows, onSelect }: { rows: DashboardFailureRow[]; onSelect: (row: DashboardFailureRow) => void }) {
  return (
    <ul className="space-y-2">
      {rows.map((row) => (
        <li key={row.ref}>
          <button
            type="button"
            onClick={() => onSelect(row)}
            className="flex w-full items-center justify-between gap-3 rounded-md border border-[var(--color-border)] bg-[var(--color-status-bad-bg)] px-3 py-2 text-left text-xs transition-colors hover:bg-[var(--color-surface-alt)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)]"
          >
            <span className="font-mono text-[var(--color-status-bad)]">#{row.ref}</span>
            <span className="font-mono text-[var(--color-text-muted)]">{row.workflowKey}</span>
            <span className="truncate text-[var(--color-text-muted)]">{row.reason ?? uiText.workflowRuns.missingValue}</span>
            <span className="font-mono text-[var(--color-text-muted)]">{formatAge(row.ageSeconds)}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}

function formatAge(seconds: number): string {
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`
  return `${Math.floor(seconds / 3600)}h`
}
