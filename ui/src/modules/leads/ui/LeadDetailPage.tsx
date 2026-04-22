import { useMemo, useState, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { Button } from '../../../shared/ui/button'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { JsonViewer } from '../../../shared/ui/JsonViewer'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { StatusBadge, type StatusTone } from '../../../shared/ui/StatusBadge'
import { useLeadSummaryQuery } from '../data/useLeadSummaryQuery'
import { formatLeadName, leadStatusTone } from '../lib/leadDisplay'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { LeadActivityEvent, LeadActivityKind, LeadLiveStatus } from '../../../shared/types/lead'

type ActivityFilter = 'ALL' | LeadActivityKind

const FILTERS: { value: ActivityFilter; label: string }[] = [
  { value: 'ALL', label: uiText.leads.detailActivityFilterAll },
  { value: 'PROCESSED_CALL', label: uiText.leads.detailActivityFilterCalls },
  { value: 'WORKFLOW_RUN', label: uiText.leads.detailActivityFilterRuns },
  { value: 'WEBHOOK_EVENT', label: uiText.leads.detailActivityFilterWebhooks },
]

export function LeadDetailPage() {
  const { sourceLeadId: rawSourceLeadId } = useParams<{ sourceLeadId: string }>()
  const sourceLeadId = rawSourceLeadId ? decodeURIComponent(rawSourceLeadId) : undefined
  const [searchParams] = useSearchParams()
  const sourceSystem = searchParams.get('sourceSystem') ?? undefined
  const [includeLive, setIncludeLive] = useState(false)
  const [activityFilter, setActivityFilter] = useState<ActivityFilter>('ALL')
  const queryClient = useQueryClient()

  const summaryQuery = useLeadSummaryQuery(sourceLeadId, { sourceSystem, includeLive })

  const filteredActivity = useMemo<LeadActivityEvent[]>(() => {
    const events = summaryQuery.data?.activity ?? []
    if (activityFilter === 'ALL') {
      return events
    }
    return events.filter((event) => event.kind === activityFilter)
  }, [activityFilter, summaryQuery.data?.activity])

  const backLink = resolveBackLink(searchParams.get('backTo'))

  const handleRefresh = async () => {
    setIncludeLive(true)
    if (!sourceLeadId) {
      return
    }
    await queryClient.invalidateQueries({
      queryKey: queryKeys.leads.summary(sourceLeadId, { sourceSystem, includeLive: true }),
    })
  }

  if (!sourceLeadId) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.leads.detailTitlePrefix} subtitle={uiText.leads.subtitle}>
          <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
            {uiText.leads.detailBackLabel}
          </Link>
        </PageHeader>
        <PageCard title={uiText.states.errorTitle}>
          <p className="text-sm text-[var(--color-text-muted)]">{uiText.leads.detailNotFound}</p>
        </PageCard>
      </div>
    )
  }

  if (summaryQuery.isPending) {
    return <LoadingState />
  }

  if (summaryQuery.isError || !summaryQuery.data) {
    return (
      <div className="space-y-4">
        <PageHeader
          title={`${uiText.leads.detailTitlePrefix} ${sourceLeadId}`}
          subtitle={uiText.leads.subtitle}
        >
          <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
            {uiText.leads.detailBackLabel}
          </Link>
        </PageHeader>
        <ErrorState message={uiText.leads.detailNotFound} />
      </div>
    )
  }

  const summary = summaryQuery.data
  const { lead } = summary
  const displayName = formatLeadName(lead.snapshot) ?? uiText.leads.nameUnknown

  return (
    <div className="space-y-4">
      <PageHeader title={displayName} subtitle={`${lead.sourceSystem} · ${lead.sourceLeadId}`}>
        <div className="flex items-center gap-3">
          <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
            {uiText.leads.detailBackLabel}
          </Link>
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={handleRefresh}
            aria-label={uiText.leads.detailRefreshAriaLabel}
            disabled={summaryQuery.isFetching}
          >
            {uiText.leads.detailRefreshAction}
          </Button>
        </div>
      </PageHeader>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
        <div className="space-y-4">
          <PageCard title={uiText.leads.detailActivityTitle}>
            <div
              role="tablist"
              aria-label={uiText.leads.detailActivityTitle}
              className="mb-3 flex gap-2"
            >
              {FILTERS.map((filter) => (
                <Button
                  key={filter.value}
                  type="button"
                  size="sm"
                  variant={activityFilter === filter.value ? 'default' : 'outline'}
                  onClick={() => setActivityFilter(filter.value)}
                  aria-pressed={activityFilter === filter.value}
                  role="tab"
                >
                  {filter.label}
                </Button>
              ))}
            </div>
            {filteredActivity.length === 0 ? (
              <p className="text-sm text-[var(--color-text-muted)]">
                {uiText.leads.detailActivityEmpty}
              </p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)]">
                {filteredActivity.map((event) => (
                  <li
                    key={`${event.kind}-${event.refId}`}
                    className="flex items-start justify-between gap-4 py-2"
                    data-testid="lead-activity-row"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <StatusBadge
                          label={formatActivityKind(event.kind)}
                          tone={activityKindTone(event.kind)}
                        />
                        {event.status ? (
                          <span className="text-xs text-[var(--color-text-muted)]">
                            {event.status}
                          </span>
                        ) : null}
                      </div>
                      {event.summary ? (
                        <p className="mt-1 truncate text-sm">{event.summary}</p>
                      ) : null}
                    </div>
                    <span className="whitespace-nowrap text-xs text-[var(--color-text-muted)]">
                      {formatDateTime(event.occurredAt)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </PageCard>

          <PageCard title={uiText.leads.detailRawSnapshotTitle}>
            <JsonViewer value={lead.snapshot} />
          </PageCard>
        </div>

        <div className="space-y-4">
          <PageCard title={uiText.leads.detailRailIdentifiersTitle}>
            <dl className="space-y-2 text-sm">
              <RailRow label={uiText.leads.tableSourceSystemHeader} value={lead.sourceSystem} />
              <RailRow
                label={uiText.leads.tableSourceLeadIdHeader}
                value={<span className="font-mono text-xs">{lead.sourceLeadId}</span>}
              />
              <RailRow
                label={uiText.leads.tableStatusHeader}
                value={<StatusBadge label={lead.status} tone={leadStatusTone(lead.status)} />}
              />
            </dl>
          </PageCard>

          <PageCard title={uiText.leads.detailRailTimestampsTitle}>
            <dl className="space-y-2 text-sm">
              <RailRow label={uiText.leads.tableUpdatedAtHeader} value={formatDateTime(lead.updatedAt)} />
              <RailRow label={uiText.leads.tableLastSyncedHeader} value={formatDateTime(lead.lastSyncedAt)} />
              <RailRow label="Created" value={formatDateTime(lead.createdAt)} />
            </dl>
          </PageCard>

          <PageCard title={uiText.leads.detailRailLiveTitle}>
            <p className="text-sm">
              <StatusBadge label={formatLiveStatus(summary.liveStatus)} tone={liveStatusTone(summary.liveStatus)} />
            </p>
            {summary.liveMessage ? (
              <p className="mt-2 text-xs text-[var(--color-text-muted)]">{summary.liveMessage}</p>
            ) : null}
          </PageCard>
        </div>
      </div>
    </div>
  )
}

function RailRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-[var(--color-text-muted)]">{label}</dt>
      <dd className="text-right">{value}</dd>
    </div>
  )
}

function formatActivityKind(kind: LeadActivityKind): string {
  switch (kind) {
    case 'PROCESSED_CALL':
      return uiText.leads.detailActivityFilterCalls
    case 'WORKFLOW_RUN':
      return uiText.leads.detailActivityFilterRuns
    case 'WEBHOOK_EVENT':
      return uiText.leads.detailActivityFilterWebhooks
    default:
      return kind
  }
}

function activityKindTone(kind: LeadActivityKind): StatusTone {
  switch (kind) {
    case 'PROCESSED_CALL':
      return 'info'
    case 'WORKFLOW_RUN':
      return 'success'
    case 'WEBHOOK_EVENT':
      return 'warning'
    default:
      return 'info'
  }
}

function formatLiveStatus(status: LeadLiveStatus): string {
  switch (status) {
    case 'LIVE_OK':
      return uiText.leads.detailLiveOkLabel
    case 'LIVE_FAILED':
      return uiText.leads.detailLiveFailedLabel
    case 'LIVE_SKIPPED':
    default:
      return uiText.leads.detailLiveSkippedLabel
  }
}

function liveStatusTone(status: LeadLiveStatus): StatusTone {
  switch (status) {
    case 'LIVE_OK':
      return 'success'
    case 'LIVE_FAILED':
      return 'error'
    case 'LIVE_SKIPPED':
    default:
      return 'info'
  }
}

function resolveBackLink(backTo: string | null): string {
  if (!backTo) {
    return routes.leads
  }
  if (!backTo.startsWith('/admin-ui')) {
    return routes.leads
  }
  if (backTo.includes('://')) {
    return routes.leads
  }
  return backTo
}
