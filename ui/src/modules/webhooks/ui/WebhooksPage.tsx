import { useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { DateInput } from '../../../shared/ui/DateInput'
import { FilterBar } from '../../../shared/ui/FilterBar'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { Input } from '../../../shared/ui/input'
import { ApplyIcon, FilterIcon, NextIcon, ResetIcon } from '../../../shared/ui/icons'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { Select } from '../../../shared/ui/select'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { WebhookFeedItem } from '../../../shared/types/webhook'
import { useWebhookDetailQuery } from '../data/useWebhookDetailQuery'
import { useWebhookListQuery } from '../data/useWebhookListQuery'
import {
  createSearchParamsFromState,
  parseWebhookSearchParams,
  toDraftFilters,
  type WebhookFilterDraft,
  type WebhookPageSearchState,
} from '../lib/webhookSearchParams'

export function WebhooksPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchState = useMemo(() => parseWebhookSearchParams(searchParams), [searchParams])
  const [draftFilters, setDraftFilters] = useState<WebhookFilterDraft>(() => toDraftFilters(searchState))

  const listQuery = useWebhookListQuery({
    source: searchState.source,
    status: searchState.status,
    eventType: searchState.eventType,
    from: searchState.from,
    to: searchState.to,
    cursor: searchState.cursor,
  })
  const detailQuery = useWebhookDetailQuery(searchState.selectedId)

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
        render: (row) => row.eventType,
      },
      {
        key: 'status',
        header: uiText.webhooks.tableStatusHeader,
        render: (row) => <StatusBadge label={row.status} tone="info" />,
      },
      {
        key: 'receivedAt',
        header: uiText.webhooks.tableReceivedAtHeader,
        render: (row) => row.receivedAt,
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

    setSearchParams(createSearchParamsFromState(nextState))
  }

  const handleReset = () => {
    setDraftFilters({
      source: 'ALL',
      status: 'ALL',
      eventType: '',
      from: '',
      to: '',
    })
    setSearchParams(new URLSearchParams())
  }

  const handleNext = () => {
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

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.webhooks.title} subtitle={uiText.webhooks.subtitle} />

      <FilterBar
        actions={
          <>
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
        <ControlGroup label={uiText.webhooks.filterSourceLabel}>
          <FilterIcon className="text-[var(--color-text-muted)]" />
          <Select
            aria-label={uiText.webhooks.filterSourceLabel}
            value={draftFilters.source}
            onChange={(event) => setDraftFilters((existing) => ({ ...existing, source: event.target.value as WebhookFilterDraft['source'] }))}
            className="w-[138px]"
          >
            <option value="ALL">{uiText.webhooks.filterSourceAll}</option>
            <option value="FUB">FUB</option>
          </Select>
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterStatusLabel}>
          <FilterIcon className="text-[var(--color-text-muted)]" />
          <Select
            aria-label={uiText.webhooks.filterStatusLabel}
            value={draftFilters.status}
            onChange={(event) => setDraftFilters((existing) => ({ ...existing, status: event.target.value as WebhookFilterDraft['status'] }))}
            className="w-[138px]"
          >
            <option value="ALL">{uiText.webhooks.filterStatusAll}</option>
            <option value="RECEIVED">RECEIVED</option>
          </Select>
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterEventTypeLabel}>
          <FilterIcon className="text-[var(--color-text-muted)]" />
          <Input
            aria-label={uiText.webhooks.filterEventTypeLabel}
            placeholder={uiText.webhooks.filterEventTypePlaceholder}
            value={draftFilters.eventType}
            onChange={(event) => setDraftFilters((existing) => ({ ...existing, eventType: event.target.value }))}
            className="w-[180px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterFromLabel}>
          <DateInput
            aria-label={uiText.webhooks.filterFromLabel}
            value={draftFilters.from}
            onChange={(event) => setDraftFilters((existing) => ({ ...existing, from: event.target.value }))}
            className="w-[170px]"
          />
        </ControlGroup>

        <ControlGroup label={uiText.webhooks.filterToLabel}>
          <DateInput
            aria-label={uiText.webhooks.filterToLabel}
            value={draftFilters.to}
            onChange={(event) => setDraftFilters((existing) => ({ ...existing, to: event.target.value }))}
            className="w-[170px]"
          />
        </ControlGroup>
      </FilterBar>

      <PageCard title={uiText.webhooks.tableTitle}>
        {listQuery.isError ? (
          <ErrorState message={uiText.states.errorMessage} />
        ) : listQuery.isPending ? (
          <LoadingState />
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={listQuery.data?.items ?? []}
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
                disabled={!listQuery.data?.nextCursor}
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
      <DetailRow label={uiText.webhooks.detailEventTypeLabel} value={detail.eventType} />
      <DetailRow label={uiText.webhooks.detailStatusLabel} value={detail.status} />
      <DetailRow label={uiText.webhooks.detailPayloadHashLabel} value={detail.payloadHash ?? '-'} mono />
      <DetailRow label={uiText.webhooks.detailReceivedAtLabel} value={detail.receivedAt} mono />
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
