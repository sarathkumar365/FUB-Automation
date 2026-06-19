import { useMemo, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { formatWorkflowRunStatus, getWorkflowRunStatusTone } from '../../workflow-runs/lib/workflowRunsDisplay'
import type { WorkflowRunSummary } from '../../workflows/lib/workflowSchemas'
import { useDashboardSnapshotQuery } from '../data/useDashboardSnapshotQuery'
import './DashboardPage.css'

type StatTone = 'default' | 'error' | 'ok'

export function DashboardPage() {
  const snapshotQuery = useDashboardSnapshotQuery()
  const snapshot = snapshotQuery.data

  const panelRegion = useMemo(
    () => ({
      title: uiText.dashboard.panelTitle,
      body: (
        <div className="space-y-2 text-sm">
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.activeWorkflowsTitle}: </span>
            {snapshot?.activeWorkflows.count ?? 0}
          </p>
          <p>
            <span className="text-[var(--color-text-muted)]">{uiText.dashboard.failedRunsTitle}: </span>
            {snapshot?.failedRuns.count ?? 0}
          </p>
        </div>
      ),
    }),
    [snapshot?.activeWorkflows.count, snapshot?.failedRuns.count],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.dashboard.inspectorTitle,
      body: <p className="text-sm text-[var(--color-text-muted)]">{uiText.dashboard.inspectorDescription}</p>,
    }),
    [],
  )

  useShellRegionRegistration({
    panel: panelRegion,
    inspector: inspectorRegion,
  })

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

  const recentRuns = snapshot.recentRuns.items.slice(0, 5)
  const failedCount = snapshot.failedRuns.count
  const failedTone: StatTone = failedCount > 0 ? 'error' : 'ok'
  const failedSubLabel =
    failedCount > 0 ? uiText.dashboard.statFailedRunsLabel : uiText.dashboard.failedRunsEmpty
  const latestIngestValue = formatNullableDate(snapshot.systemHealth.latestWebhookReceivedAt)

  return (
    <div className="dash-root space-y-4">
      <div className="dash-blob dash-blob-tr" aria-hidden="true" />
      <div className="dash-blob dash-blob-bl" aria-hidden="true" />

      <PageHeader title={uiText.dashboard.title} subtitle={uiText.dashboard.subtitle} />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile
          label={uiText.dashboard.activeWorkflowsTitle}
          subLabel={uiText.dashboard.statActiveWorkflowsLabel}
          value={snapshot.activeWorkflows.count}
          to={buildRouteWithStatus(routes.workflows, 'ACTIVE')}
          linkLabel={uiText.dashboard.openWorkflows}
          tone="default"
          delay={0}
        />
        <StatTile
          label={uiText.dashboard.failedRunsTitle}
          subLabel={failedSubLabel}
          value={failedCount}
          to={buildRouteWithStatus(routes.workflowRuns, 'FAILED')}
          linkLabel={uiText.dashboard.openFailedRuns}
          tone={failedTone}
          delay={60}
        />
        <StatTile
          label={uiText.dashboard.systemHealthTitle}
          subLabel={`${uiText.dashboard.latestIngestLabel}: ${latestIngestValue}`}
          value={snapshot.systemHealth.recentWebhookCount}
          to={routes.webhooks}
          linkLabel={uiText.dashboard.viewIngestActivity}
          tone="default"
          delay={120}
        />
      </div>

      <div className="dash-item" style={{ ['--delay' as string]: '180ms' } as CSSProperties}>
        <PageCard title={uiText.dashboard.recentRunsTitle}>
          {recentRuns.length === 0 ? (
            <p className="text-sm text-[var(--color-text-muted)]">{uiText.dashboard.recentRunsEmpty}</p>
          ) : (
            <RunList runs={recentRuns} />
          )}
          <DashboardLink to={routes.workflowRuns} label={uiText.dashboard.openRuns} />
        </PageCard>
      </div>

      <p className="dash-item text-xs text-[var(--color-text-muted)]" style={{ ['--delay' as string]: '240ms' } as CSSProperties}>
        {uiText.dashboard.placeholderHealthMessage}
      </p>
    </div>
  )
}

type StatTileProps = {
  label: string
  subLabel: string
  value: number
  to: string
  linkLabel: string
  tone: StatTone
  delay: number
}

function StatTile({ label, subLabel, value, to, linkLabel, tone, delay }: StatTileProps) {
  const accentColor =
    tone === 'error'
      ? 'var(--color-status-bad)'
      : tone === 'ok'
        ? 'var(--color-status-ok)'
        : 'var(--color-brand)'

  return (
    <Link
      to={to}
      aria-label={linkLabel}
      className="stat-tile dash-item group flex flex-col rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-subtle)] no-underline"
      style={
        {
          borderLeftColor: accentColor,
          borderLeftWidth: '3px',
          ['--delay' as string]: `${delay}ms`,
        } as CSSProperties
      }
    >
      <h3 className="text-xs font-medium uppercase tracking-wide text-[var(--color-text-muted)]">{label}</h3>
      <span className="mt-2 text-3xl font-semibold leading-none text-[var(--color-text)]">{value}</span>
      <span className="mt-1 text-xs text-[var(--color-text-muted)]">{subLabel}</span>
      <span className="mt-3 text-xs font-medium text-[var(--color-brand)] group-hover:underline">{linkLabel} →</span>
    </Link>
  )
}

function RunList({ runs }: { runs: WorkflowRunSummary[] }) {
  return (
    <div className="overflow-x-auto rounded-md border border-[var(--color-border)]">
      <table className="min-w-full text-left text-xs">
        <thead className="bg-[var(--color-surface-alt)] text-[var(--color-text-muted)]">
          <tr>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.runIdHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.workflowKeyHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.statusHeader}</th>
            <th className="px-2 py-2 font-medium">{uiText.dashboard.completedAtHeader}</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.id} className="border-t border-[var(--color-border)]">
              <td className="px-2 py-2">{run.id}</td>
              <td className="px-2 py-2 font-mono">{run.workflowKey}</td>
              <td className="px-2 py-2">
                <StatusBadge label={formatWorkflowRunStatus(run.status)} tone={getWorkflowRunStatusTone(run.status)} />
              </td>
              <td className="px-2 py-2">{formatNullableDate(run.completedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function DashboardLink({ to, label }: { to: string; label: string }) {
  return (
    <Link
      to={to}
      className="mt-3 inline-flex h-8 items-center justify-center rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-xs font-medium text-[var(--color-text)] transition-colors hover:bg-[var(--color-surface-alt)]"
    >
      {label}
    </Link>
  )
}

function buildRouteWithStatus(path: string, status: string): string {
  const params = new URLSearchParams()
  params.set('status', status)
  return `${path}?${params.toString()}`
}

function formatNullableDate(value: string | null): string {
  if (!value) {
    return uiText.workflowRuns.missingValue
  }
  return formatDateTime(value)
}
