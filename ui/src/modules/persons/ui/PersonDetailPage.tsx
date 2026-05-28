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
import { usePersonSummaryQuery } from '../data/usePersonSummaryQuery'
import { formatPersonName, personStatusTone } from '../lib/personDisplay'
import { queryKeys } from '../../../platform/query/queryKeys'
import type { PersonActivityEvent, PersonActivityKind, PersonLiveStatus } from '../../../shared/types/person'

type ActivityFilter = 'ALL' | PersonActivityKind

const FILTERS: { value: ActivityFilter; label: string }[] = [
  { value: 'ALL', label: uiText.persons.detailActivityFilterAll },
  { value: 'PROCESSED_CALL', label: uiText.persons.detailActivityFilterCalls },
  { value: 'WORKFLOW_RUN', label: uiText.persons.detailActivityFilterRuns },
  { value: 'WEBHOOK_EVENT', label: uiText.persons.detailActivityFilterWebhooks },
]

export function PersonDetailPage() {
  const { sourcePersonId: rawSourcePersonId } = useParams<{ sourcePersonId: string }>()
  const sourcePersonId = rawSourcePersonId ? decodeURIComponent(rawSourcePersonId) : undefined
  const [searchParams] = useSearchParams()
  const sourceSystem = searchParams.get('sourceSystem') ?? undefined
  const [includeLive, setIncludeLive] = useState(false)
  const [activityFilter, setActivityFilter] = useState<ActivityFilter>('ALL')
  const queryClient = useQueryClient()

  const summaryQuery = usePersonSummaryQuery(sourcePersonId, { sourceSystem, includeLive })

  const filteredActivity = useMemo<PersonActivityEvent[]>(() => {
    const events = summaryQuery.data?.activity ?? []
    if (activityFilter === 'ALL') {
      return events
    }
    return events.filter((event) => event.kind === activityFilter)
  }, [activityFilter, summaryQuery.data?.activity])

  const backLink = resolveBackLink(searchParams.get('backTo'))

  const handleRefresh = async () => {
    setIncludeLive(true)
    if (!sourcePersonId) {
      return
    }
    await queryClient.invalidateQueries({
      queryKey: queryKeys.persons.summary(sourcePersonId, { sourceSystem, includeLive: true }),
    })
  }

  if (!sourcePersonId) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.persons.detailTitlePrefix} subtitle={uiText.persons.subtitle}>
          <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
            {uiText.persons.detailBackLabel}
          </Link>
        </PageHeader>
        <PageCard title={uiText.states.errorTitle}>
          <p className="text-sm text-[var(--color-text-muted)]">{uiText.persons.detailNotFound}</p>
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
          title={`${uiText.persons.detailTitlePrefix} ${sourcePersonId}`}
          subtitle={uiText.persons.subtitle}
        >
          <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
            {uiText.persons.detailBackLabel}
          </Link>
        </PageHeader>
        <ErrorState message={uiText.persons.detailNotFound} />
      </div>
    )
  }

  const summary = summaryQuery.data
  const { person } = summary
  const displayName = formatPersonName(person.snapshot) ?? uiText.persons.nameUnknown

  return (
    <div className="space-y-4">
      <PageHeader title={displayName} subtitle={`${person.sourceSystem} · ${person.sourcePersonId}`}>
        <div className="flex items-center gap-3">
          <Link className="text-sm text-[var(--color-brand)] underline" to={backLink}>
            {uiText.persons.detailBackLabel}
          </Link>
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={handleRefresh}
            aria-label={uiText.persons.detailRefreshAriaLabel}
            disabled={summaryQuery.isFetching}
          >
            {uiText.persons.detailRefreshAction}
          </Button>
        </div>
      </PageHeader>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
        <div className="space-y-4">
          <PageCard title={uiText.persons.detailActivityTitle}>
            <div
              role="tablist"
              aria-label={uiText.persons.detailActivityTitle}
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
                {uiText.persons.detailActivityEmpty}
              </p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)]">
                {filteredActivity.map((event) => (
                  <li
                    key={`${event.kind}-${event.refId}`}
                    className="flex items-start justify-between gap-4 py-2"
                    data-testid="person-activity-row"
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

          <PageCard title={uiText.persons.detailRawSnapshotTitle}>
            <JsonViewer value={person.snapshot} />
          </PageCard>
        </div>

        <div className="space-y-4">
          <PageCard title={uiText.persons.detailRailIdentifiersTitle}>
            <dl className="space-y-2 text-sm">
              <RailRow label={uiText.persons.tableSourceSystemHeader} value={person.sourceSystem} />
              <RailRow
                label={uiText.persons.tableSourcePersonIdHeader}
                value={<span className="font-mono text-xs">{person.sourcePersonId}</span>}
              />
              <RailRow
                label={uiText.persons.tableStatusHeader}
                value={<StatusBadge label={person.status} tone={personStatusTone(person.status)} />}
              />
            </dl>
          </PageCard>

          <PageCard title={uiText.persons.detailRailTimestampsTitle}>
            <dl className="space-y-2 text-sm">
              <RailRow label={uiText.persons.tableUpdatedAtHeader} value={formatDateTime(person.updatedAt)} />
              <RailRow label={uiText.persons.tableLastSyncedHeader} value={formatDateTime(person.lastSyncedAt)} />
              <RailRow label="Created" value={formatDateTime(person.createdAt)} />
            </dl>
          </PageCard>

          <PageCard title={uiText.persons.detailRailLiveTitle}>
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

function formatActivityKind(kind: PersonActivityKind): string {
  switch (kind) {
    case 'PROCESSED_CALL':
      return uiText.persons.detailActivityFilterCalls
    case 'WORKFLOW_RUN':
      return uiText.persons.detailActivityFilterRuns
    case 'WEBHOOK_EVENT':
      return uiText.persons.detailActivityFilterWebhooks
    default:
      return kind
  }
}

function activityKindTone(kind: PersonActivityKind): StatusTone {
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

function formatLiveStatus(status: PersonLiveStatus): string {
  switch (status) {
    case 'LIVE_OK':
      return uiText.persons.detailLiveOkLabel
    case 'LIVE_FAILED':
      return uiText.persons.detailLiveFailedLabel
    case 'LIVE_SKIPPED':
    default:
      return uiText.persons.detailLiveSkippedLabel
  }
}

function liveStatusTone(status: PersonLiveStatus): StatusTone {
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
    return routes.persons
  }
  if (!backTo.startsWith('/admin-ui')) {
    return routes.persons
  }
  if (backTo.includes('://')) {
    return routes.persons
  }
  return backTo
}
