import { useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { useWebhookStream } from '../../../platform/stream/useWebhookStream'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { formatWebhookEventType, formatWebhookReceivedAt } from '../../../shared/lib/webhookDisplay'
import { Button } from '../../../shared/ui/button'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { DateInput } from '../../../shared/ui/DateInput'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { FilterBar } from '../../../shared/ui/FilterBar'
import { Input } from '../../../shared/ui/input'
import { ApplyIcon, FilterIcon, NextIcon, PauseIcon, ResetIcon, ResumeIcon } from '../../../shared/ui/icons'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { Select } from '../../../shared/ui/select'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { WebhookFeedItem } from '../../../shared/types/webhook'
import { useWebhookDetailQuery } from '../data/useWebhookDetailQuery'
import { useWebhookListQuery } from '../data/useWebhookListQuery'
import { filterWebhookRows, mergeWebhookRows, toWebhookFeedItems } from '../lib/webhookLiveRows'
import {
  createSearchParamsFromState,
  parseWebhookSearchParams,
  toDraftFilters,
  type WebhookFilterDraft,
  type WebhookPageSearchState,
} from '../lib/webhookSearchParams'

const MAX_ACTIVITY_TICKS = 20

export function WebhooksPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchState = useMemo(() => parseWebhookSearchParams(searchParams), [searchParams])
  const [isPaused, setIsPaused] = useState(false)
  const filterDraftKey = useMemo(
    () => [searchState.source ?? '', searchState.status ?? '', searchState.eventType ?? '', searchState.from ?? '', searchState.to ?? ''].join('|'),
    [searchState.eventType, searchState.from, searchState.source, searchState.status, searchState.to],
  )
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: WebhookFilterDraft }>(() => ({
    key: filterDraftKey,
    value: toDraftFilters(searchState),
  }))
  const draftFilters = draftFilterState.key === filterDraftKey ? draftFilterState.value : toDraftFilters(searchState)

  const [pausedBaseRows, setPausedBaseRows] = useState<WebhookFeedItem[]>([])
  const [pausedAppliedFilters, setPausedAppliedFilters] = useState<WebhookPageSearchState>(() => extractFilterState(searchState))

  const listQuery = useWebhookListQuery(
    {
      source: searchState.source,
      status: searchState.status,
      eventType: searchState.eventType,
      from: searchState.from,
      to: searchState.to,
      cursor: searchState.cursor,
    },
    !isPaused,
  )
  const detailQuery = useWebhookDetailQuery(searchState.selectedId)
  const stream = useWebhookStream(
    {
      source: searchState.source,
      status: searchState.status,
      eventType: searchState.eventType,
    },
    { enabled: !isPaused },
  )
  const streamRows = useMemo(() => toWebhookFeedItems(stream.events), [stream.events])
  const activityTicks = useMemo(() => streamRows.slice(0, MAX_ACTIVITY_TICKS).map((row) => row.id), [streamRows])

  const serverRows = listQuery.data?.items ?? []
  const unpausedRows = searchState.cursor ? serverRows : mergeWebhookRows(streamRows, serverRows)
  const tableRows = isPaused ? filterWebhookRows(pausedBaseRows, pausedAppliedFilters) : unpausedRows
  const hasBufferedRows = searchState.cursor !== undefined && streamRows.length > 0

  const columns = useMemo<ColumnDef<WebhookFeedItem>[]>(
    () => [
      {
        key: 'eventId',
        header: uiText.webhooks.tableEventIdHeader,
        render: (row) => <span className="font-mono text-xs">{row.eventId}</span>,
      },
      {
        key: 'source',
        header: uiText.webhooks.tableSourceHeader,
        render: (row) => row.source,
      },
      {
        key: 'eventType',
        header: uiText.webhooks.tableEventTypeHeader,
        render: (row) => formatWebhookEventType(row.eventType),
      },
      {
        key: 'status',
        header: uiText.webhooks.tableStatusHeader,
        render: (row) => <StatusBadge label={row.status} tone="info" />,
      },
      {
        key: 'receivedAt',
        header: uiText.webhooks.tableReceivedAtHeader,
        render: (row) => formatWebhookReceivedAt(row.receivedAt),
      },
    ],
    [],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.webhooks.inspectorTitle,
      body: buildInspectorBody({
        selectedId: searchState.selectedId,
        detailQueryState: {
          isPending: detailQuery.isPending,
          isError: detailQuery.isError,
          data: detailQuery.data,
        },
      }),
    }),
    [detailQuery.data, detailQuery.isError, detailQuery.isPending, searchState.selectedId],
  )

  useShellRegionRegistration({
    panel: null,
    inspector: inspectorRegion,
  })

  const handlePauseResume = () => {
    if (isPaused) {
      setIsPaused(false)
      return
    }

    setPausedBaseRows(mergeWebhookRows(streamRows, unpausedRows))
    setPausedAppliedFilters(extractFilterState(searchState))
    setIsPaused(true)
  }

  const handleApply = () => {
    const nextState: WebhookPageSearchState = {
      source: draftFilters.source === 'ALL' ? undefined : draftFilters.source,
      status: draftFilters.status === 'ALL' ? undefined : draftFilters.status,
      eventType: draftFilters.eventType || undefined,
      from: draftFilters.from || undefined,
      to: draftFilters.to || undefined,
      cursor: undefined,
      selectedId: undefined,
    }

    if (isPaused) {
      setPausedAppliedFilters(extractFilterState(nextState))
    }

    setSearchParams(createSearchParamsFromState(nextState))
  }

  const handleReset = () => {
    setDraftFilterState({
      key: filterDraftKey,
      value: {
        source: 'ALL',
        status: 'ALL',
        eventType: '',
        from: '',
        to: '',
      },
    })

    if (isPaused) {
      setPausedAppliedFilters({})
    }

    setSearchParams(new URLSearchParams())
  }

  const handleViewLatest = () => {
    if (!hasBufferedRows) {
      return
    }

    if (isPaused) {
      setPausedBaseRows((existing) => mergeWebhookRows(streamRows, existing))
    }
    setSearchParams(
      createSearchParamsFromState({
        ...searchState,
        cursor: undefined,
        selectedId: undefined,
      }),
    )
  }

  const handleNext = () => {
    if (isPaused) {
      return
    }

    const nextCursor = listQuery.data?.nextCursor
    if (!nextCursor) {
      return
    }

    setSearchParams(
      createSearchParamsFromState({
        ...searchState,
        cursor: nextCursor,
        selectedId: undefined,
      }),
    )
  }

  const handleSelectRow = (row: WebhookFeedItem) => {
    setSearchParams(
      createSearchParamsFromState({
        ...searchState,
        selectedId: row.id,
      }),
    )
  }

  const liveStatus = getLiveStatus(isPaused, stream.state)
  const showLoadingState = !isPaused && listQuery.isPending
  const showErrorState = !isPaused && listQuery.isError

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.webhooks.title} subtitle={uiText.webhooks.subtitle}>
        <div data-testid="webhook-live-controls" className="flex flex-col items-end gap-1">
          <div className="flex items-center gap-2">
            <div
              data-testid="webhook-live-state"
              className={`inline-flex items-center gap-2 rounded-full border px-2.5 py-1 text-xs font-semibold ${liveStatus.className}`}
            >
              <span className={`inline-block h-2 w-2 rounded-full ${liveStatus.dotClassName}`} />
              {liveStatus.label}
            </div>
            <Button
              type="button"
              size="icon"
              variant="outline"
              onClick={handlePauseResume}
              aria-label={isPaused ? uiText.webhooks.resumeStreamAria : uiText.webhooks.pauseStreamAria}
            >
              {isPaused ? <ResumeIcon /> : <PauseIcon />}
            </Button>
          </div>
          <span data-testid="webhook-heartbeat" className="font-mono text-xs text-[var(--color-text-muted)]">
            {uiText.webhooks.heartbeatLabel}:{' '}
            {stream.lastHeartbeatAt ? formatDateTime(stream.lastHeartbeatAt) : '-'}
          </span>
        </div>
      </PageHeader>

      <FilterBar
        actions={
          <>
            {hasBufferedRows ? (
              <Button type="button" size="sm" variant="secondary" onClick={handleViewLatest} aria-label={uiText.webhooks.viewLatestAria}>
                {uiText.webhooks.viewLatest} ({streamRows.length})
              </Button>
            ) : null}
            <Button type="button" size="sm" onClick={handleApply} aria-label={uiText.filters.apply}>
              <ApplyIcon className="mr-1.5" />
              {uiText.filters.apply}
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={handleReset} aria-label={uiText.filters.reset}>
              <ResetIcon className="mr-1.5" />
              {uiText.filters.reset}
            </Button>
          </>
        }
      >
        <FilterIcon data-testid="webhook-filter-icon" className="text-[var(--color-text-muted)]" />

        <ControlGroup label={uiText.webhooks.filterSourceLabel}>
          <Select
            aria-label={uiText.webhooks.filterSourceLabel}
            value={draftFilters.source}
            onChange={(event) =>
              setDraftFilterState((existing) => ({
                key: filterDraftKey,
                value: {
                  ...(existing.key === filterDraftKey ? existing.value : draftFilters),
                  source: event.target.value as WebhookFilterDraft['source'],
                },
              }))
            }
            className="w-[138px]"
          >
            <option value="ALL">{uiText.webhooks.filterSourceAll}</option>
            <option value="FUB">FUB</option>
          </Select>
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterStatusLabel}>
          <Select
            aria-label={uiText.webhooks.filterStatusLabel}
            value={draftFilters.status}
            onChange={(event) =>
              setDraftFilterState((existing) => ({
                key: filterDraftKey,
                value: {
                  ...(existing.key === filterDraftKey ? existing.value : draftFilters),
                  status: event.target.value as WebhookFilterDraft['status'],
                },
              }))
            }
            className="w-[138px]"
          >
            <option value="ALL">{uiText.webhooks.filterStatusAll}</option>
            <option value="RECEIVED">RECEIVED</option>
          </Select>
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterEventTypeLabel}>
          <Input
            aria-label={uiText.webhooks.filterEventTypeLabel}
            placeholder={uiText.webhooks.filterEventTypePlaceholder}
            value={draftFilters.eventType}
            onChange={(event) =>
              setDraftFilterState((existing) => ({
                key: filterDraftKey,
                value: {
                  ...(existing.key === filterDraftKey ? existing.value : draftFilters),
                  eventType: event.target.value,
                },
              }))
            }
            className="w-[180px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterFromLabel}>
          <DateInput
            aria-label={uiText.webhooks.filterFromLabel}
            value={draftFilters.from}
            onChange={(event) =>
              setDraftFilterState((existing) => ({
                key: filterDraftKey,
                value: {
                  ...(existing.key === filterDraftKey ? existing.value : draftFilters),
                  from: event.target.value,
                },
              }))
            }
            className="w-[170px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterToLabel}>
          <DateInput
            aria-label={uiText.webhooks.filterToLabel}
            value={draftFilters.to}
            onChange={(event) =>
              setDraftFilterState((existing) => ({
                key: filterDraftKey,
                value: {
                  ...(existing.key === filterDraftKey ? existing.value : draftFilters),
                  to: event.target.value,
                },
              }))
            }
            className="w-[170px]"
          />
        </ControlGroup>
      </FilterBar>

      <PageCard title={uiText.webhooks.tableTitle}>
        <ActivityTickStrip ticks={activityTicks} heartbeatPulseToken={stream.lastHeartbeatAt} />

        {showErrorState ? (
          <ErrorState message={uiText.states.errorMessage} />
        ) : showLoadingState ? (
          <LoadingState />
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={tableRows}
              getRowKey={(row) => row.id}
              getRowAriaLabel={(row) => `${uiText.webhooks.rowAriaLabelPrefix} ${row.eventId}`}
              onRowClick={handleSelectRow}
              selectedRowKey={searchState.selectedId ?? null}
              emptyMessage={uiText.webhooks.tableEmptyMessage}
            />
            <div className="mt-3 flex items-center justify-end gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={handleNext}
                disabled={isPaused || !listQuery.data?.nextCursor}
                aria-label={uiText.webhooks.paginationNextAria}
              >
                <NextIcon className="mr-1.5" />
                {listQuery.data?.nextCursor ? uiText.webhooks.paginationNext : uiText.webhooks.paginationNoMore}
              </Button>
            </div>
          </>
        )}
      </PageCard>
    </div>
  )
}

function ControlGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex items-center gap-2">
      <span className="sr-only">{label}</span>
      {children}
    </label>
  )
}

function ActivityTickStrip({ ticks, heartbeatPulseToken }: { ticks: number[]; heartbeatPulseToken: string | null }) {
  const filledCount = Math.min(ticks.length, MAX_ACTIVITY_TICKS)
  const totalSlots = MAX_ACTIVITY_TICKS
  const pulseIndex = Math.min(filledCount, totalSlots - 1)
  return (
    <div data-testid="activity-tick-strip" className="mb-3 flex w-full items-center gap-1" aria-hidden="true">
      {Array.from({ length: totalSlots }, (_, index) => (
        <span
          key={`tick-${index}-${index === pulseIndex ? (heartbeatPulseToken ?? 'idle') : 'idle'}`}
          className={`h-1.5 min-w-0 flex-1 rounded-full ${
            index < filledCount ? 'bg-[var(--color-live)]/60' : 'bg-[var(--color-border)]'
          }`}
        >
          {heartbeatPulseToken && index === pulseIndex ? (
            <span
              key={`heartbeat-pulse-${heartbeatPulseToken}`}
              data-testid="heartbeat-pulse-tick"
              // TODO: Limit this pulse to the heartbeat update cycle only. With a persistent
              // token, unrelated re-renders (for example row-count changes) can retrigger a flash.
              className="block h-full w-full rounded-full bg-[var(--color-status-warn)] animate-[heartbeat-flash_900ms_ease-out_1_forwards]"
            />
          ) : null}
        </span>
      ))}
    </div>
  )
}

function getLiveStatus(isPaused: boolean, streamState: 'connecting' | 'open' | 'error') {
  if (isPaused) {
    return {
      label: uiText.webhooks.liveStatePaused,
      className: 'border-[var(--color-border)] bg-[var(--color-surface-alt)] text-[var(--color-text-muted)]',
      dotClassName: 'bg-[var(--color-text-muted)]',
    }
  }

  if (streamState === 'open') {
    return {
      label: uiText.webhooks.liveStateLive,
      className: 'border-[var(--color-status-ok)]/30 bg-[var(--color-status-ok-bg)] text-[var(--color-status-ok)]',
      dotClassName: 'bg-[var(--color-live)]',
    }
  }

  if (streamState === 'error') {
    return {
      label: uiText.webhooks.liveStateError,
      className: 'border-[var(--color-status-bad)]/30 bg-[var(--color-status-bad-bg)] text-[var(--color-status-bad)]',
      dotClassName: 'bg-[var(--color-status-bad)]',
    }
  }

  return {
    label: uiText.webhooks.liveStateConnecting,
    className: 'border-[var(--color-status-warn)]/30 bg-[var(--color-status-warn-bg)] text-[var(--color-status-warn)]',
    dotClassName: 'bg-[var(--color-status-warn)]',
  }
}

function extractFilterState(state: WebhookPageSearchState): WebhookPageSearchState {
  return {
    source: state.source,
    status: state.status,
    eventType: state.eventType,
    from: state.from,
    to: state.to,
  }
}

function buildInspectorBody({
  selectedId,
  detailQueryState,
}: {
  selectedId: number | undefined
  detailQueryState: {
    isPending: boolean
    isError: boolean
    data:
      | {
          id: number
          eventId: string
          source: string
          eventType: string
          status: string
          payloadHash?: string | null
          payload: unknown
          receivedAt: string
        }
      | undefined
  }
}) {
  if (selectedId === undefined) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.webhooks.inspectorEmpty}</p>
  }

  if (detailQueryState.isPending) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.webhooks.inspectorLoading}</p>
  }

  if (detailQueryState.isError || !detailQueryState.data) {
    return <p className="text-sm text-[var(--color-status-bad)]">{uiText.webhooks.inspectorError}</p>
  }

  const detail = detailQueryState.data

  return (
    <section className="space-y-3 text-sm">
      <h4 className="font-semibold text-[var(--color-text)]">{uiText.webhooks.inspectorDetailTitle}</h4>
      <DetailRow label={uiText.webhooks.detailIdLabel} value={String(detail.id)} mono />
      <DetailRow label={uiText.webhooks.detailEventIdLabel} value={detail.eventId} mono />
      <DetailRow label={uiText.webhooks.detailSourceLabel} value={detail.source} />
      <DetailRow label={uiText.webhooks.detailEventTypeLabel} value={formatWebhookEventType(detail.eventType)} />
      <DetailRow label={uiText.webhooks.detailStatusLabel} value={detail.status} />
      <DetailRow label={uiText.webhooks.detailPayloadHashLabel} value={detail.payloadHash ?? '-'} mono />
      <DetailRow label={uiText.webhooks.detailReceivedAtLabel} value={formatWebhookReceivedAt(detail.receivedAt)} mono />
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">{uiText.webhooks.detailPayloadLabel}</p>
        <pre className="max-h-64 overflow-auto rounded-md border border-[var(--color-border)] bg-[var(--color-surface-alt)] p-2 text-xs text-[var(--color-text)]">
          {safeJson(detail.payload)}
        </pre>
      </div>
    </section>
  )
}

function DetailRow({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">{label}</p>
      <p className={mono ? 'font-mono text-xs text-[var(--color-text)]' : 'text-[var(--color-text)]'}>{value}</p>
    </div>
  )
}

function safeJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return '"[unserializable payload]"'
  }
}
