import { useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import type { ProcessedCallStatus, ProcessedCallSummary } from '../../../platform/ports/processedCallsPort'
import { uiText } from '../../../shared/constants/uiText'
import { useNotify } from '../../../shared/notifications/useNotify'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { DateInput } from '../../../shared/ui/DateInput'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { FilterBar } from '../../../shared/ui/FilterBar'
import { ApplyIcon, FilterIcon, ReplayIcon, ResetIcon } from '../../../shared/ui/icons'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { Select } from '../../../shared/ui/select'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useProcessedCallsQuery } from '../data/useProcessedCallsQuery'
import { type ReplayResult, useReplayProcessedCallMutation } from '../data/useReplayProcessedCallMutation'
import { formatProcessedCallDateTime, formatProcessedCallStatus, getProcessedCallStatusTone } from '../lib/processedCallDisplay'
import {
  createProcessedCallsSearchParamsFromState,
  parseProcessedCallsSearchParams,
  toProcessedCallsDraftFilters,
  type ProcessedCallsFilterDraft,
  type ProcessedCallsPageSearchState,
} from '../lib/processedCallSearchParams'

type ReplayOutcomeState = {
  callId: number
  result: ReplayResult
  at: string
}

export function ProcessedCallsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const searchState = useMemo(() => parseProcessedCallsSearchParams(searchParams), [searchParams])
  const notify = useNotify()
  const listQuery = useProcessedCallsQuery({
    status: searchState.status,
    from: searchState.from,
    to: searchState.to,
  })
  const replayMutation = useReplayProcessedCallMutation()
  const [lastReplayOutcome, setLastReplayOutcome] = useState<ReplayOutcomeState | null>(null)
  const [pendingReplayCallId, setPendingReplayCallId] = useState<number | null>(null)

  const filterDraftKey = useMemo(() => [searchState.status ?? '', searchState.from ?? '', searchState.to ?? ''].join('|'), [
    searchState.from,
    searchState.status,
    searchState.to,
  ])
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: ProcessedCallsFilterDraft }>(() => ({
    key: filterDraftKey,
    value: toProcessedCallsDraftFilters(searchState),
  }))
  const draftFilters = draftFilterState.key === filterDraftKey ? draftFilterState.value : toProcessedCallsDraftFilters(searchState)

  const rows = listQuery.data ?? []
  const selectedRow = rows.find((row) => row.callId === searchState.selectedCallId)

  const columns = useMemo<ColumnDef<ProcessedCallSummary>[]>(
    () => [
      {
        key: 'callId',
        header: uiText.processedCalls.callIdHeader,
        render: (row) => <span className="font-mono text-xs">{row.callId}</span>,
      },
      {
        key: 'status',
        header: uiText.processedCalls.statusHeader,
        render: (row) => <StatusBadge label={formatProcessedCallStatus(row.status)} tone={getProcessedCallStatusTone(row.status)} />,
      },
      {
        key: 'ruleApplied',
        header: uiText.processedCalls.ruleAppliedHeader,
        render: (row) => row.ruleApplied ?? '-',
      },
      {
        key: 'taskId',
        header: uiText.processedCalls.taskIdHeader,
        render: (row) => (row.taskId !== null && row.taskId !== undefined ? row.taskId : '-'),
      },
      {
        key: 'failureReason',
        header: uiText.processedCalls.failureReasonHeader,
        render: (row) => row.failureReason ?? '-',
      },
      {
        key: 'retryCount',
        header: uiText.processedCalls.retryCountHeader,
        render: (row) => row.retryCount,
      },
      {
        key: 'updatedAt',
        header: uiText.processedCalls.updatedAtHeader,
        render: (row) => formatProcessedCallDateTime(row.updatedAt),
      },
      {
        key: 'replay',
        header: uiText.processedCalls.replayHeader,
        className: 'w-14 text-right',
        render: (row) => {
          const replayable = row.status === 'FAILED'
          return (
            <div className="flex justify-end">
              <Button
                type="button"
                size="icon"
                variant="outline"
                aria-label={`${uiText.processedCalls.replayAriaPrefix} ${row.callId}`}
                title={replayable ? uiText.processedCalls.replayTooltip : uiText.processedCalls.replayDisabledTooltip}
                data-testid={`processed-calls-replay-${row.callId}`}
                disabled={!replayable || replayMutation.isPending}
                onClick={(event) => {
                  event.stopPropagation()
                  setPendingReplayCallId(row.callId)
                }}
              >
                <ReplayIcon />
              </Button>
            </div>
          )
        },
      },
    ],
    [replayMutation.isPending],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.processedCalls.inspectorTitle,
      body: buildInspectorBody(selectedRow, lastReplayOutcome),
    }),
    [lastReplayOutcome, selectedRow],
  )

  useShellRegionRegistration({
    panel: null,
    inspector: inspectorRegion,
  })

  const handleApply = () => {
    const nextState: ProcessedCallsPageSearchState = {
      status: draftFilters.status === 'ALL' ? undefined : draftFilters.status,
      from: draftFilters.from || undefined,
      to: draftFilters.to || undefined,
      selectedCallId: undefined,
    }

    setSearchParams(createProcessedCallsSearchParamsFromState(nextState))
  }

  const handleReset = () => {
    setDraftFilterState({
      key: filterDraftKey,
      value: {
        status: 'ALL',
        from: '',
        to: '',
      },
    })
    setSearchParams(new URLSearchParams())
  }

  const handleSelectRow = (row: ProcessedCallSummary) => {
    setSearchParams(
      createProcessedCallsSearchParamsFromState({
        ...searchState,
        selectedCallId: row.callId,
      }),
    )
  }

  const handleConfirmReplay = async () => {
    if (pendingReplayCallId === null) {
      return
    }

    const callId = pendingReplayCallId
    setPendingReplayCallId(null)
    const result = await replayMutation.mutateAsync(callId)
    setLastReplayOutcome({
      callId,
      result,
      at: new Date().toISOString(),
    })
    notifyReplayOutcome(notify, result)
  }

  const showLoadingState = listQuery.isPending
  const showErrorState = listQuery.isError

  return (
    <div className="flex h-full min-h-0 flex-col gap-4" data-testid="processed-calls-page-layout">
      <PageHeader title={uiText.processedCalls.title} subtitle={uiText.processedCalls.subtitle} />

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
        <FilterIcon className="text-[var(--color-text-muted)]" />

        <ControlGroup label={uiText.processedCalls.filterStatusLabel}>
          <Select
            aria-label={uiText.processedCalls.filterStatusLabel}
            value={draftFilters.status}
            onChange={(event) =>
              setDraftFilterState((existing) => ({
                key: filterDraftKey,
                value: {
                  ...(existing.key === filterDraftKey ? existing.value : draftFilters),
                  status: event.target.value as ProcessedCallsFilterDraft['status'],
                },
              }))
            }
            className="w-[180px]"
          >
            <option value="ALL">{uiText.processedCalls.filterStatusAll}</option>
            {PROCESSED_CALL_STATUSES.map((statusValue) => (
              <option key={statusValue} value={statusValue}>
                {formatProcessedCallStatus(statusValue)}
              </option>
            ))}
          </Select>
        </ControlGroup>

        <ControlGroup label={uiText.processedCalls.filterFromLabel}>
          <DateInput
            aria-label={uiText.processedCalls.filterFromLabel}
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

        <ControlGroup label={uiText.processedCalls.filterToLabel}>
          <DateInput
            aria-label={uiText.processedCalls.filterToLabel}
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

      <div className="min-h-0 flex-1 overflow-y-auto" data-testid="processed-calls-history-scroll">
        <PageCard title={uiText.processedCalls.tableTitle}>
          {showErrorState ? (
            <ErrorState message={uiText.states.errorMessage} />
          ) : showLoadingState ? (
            <LoadingState />
          ) : (
            <DataTable
              columns={columns}
              rows={rows}
              getRowKey={(row) => row.callId}
              onRowClick={handleSelectRow}
              selectedRowKey={searchState.selectedCallId ?? null}
              getRowAriaLabel={(row) => `${uiText.processedCalls.rowAriaLabelPrefix} ${row.callId}`}
              emptyMessage={uiText.processedCalls.emptyMessage}
            />
          )}
        </PageCard>
      </div>

      <ConfirmDialog
        open={pendingReplayCallId !== null}
        title={uiText.processedCalls.replayConfirmTitle}
        description={uiText.processedCalls.replayConfirmDescription}
        confirmLabel={uiText.processedCalls.replayConfirmAction}
        onOpenChange={(open) => {
          if (!open) {
            setPendingReplayCallId(null)
          }
        }}
        onConfirm={handleConfirmReplay}
      />
    </div>
  )
}

const PROCESSED_CALL_STATUSES: ProcessedCallStatus[] = ['RECEIVED', 'PROCESSING', 'SKIPPED', 'TASK_CREATED', 'FAILED']

function ControlGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex items-center gap-2">
      <span className="sr-only">{label}</span>
      {children}
    </label>
  )
}

function buildInspectorBody(selectedRow: ProcessedCallSummary | undefined, outcome: ReplayOutcomeState | null) {
  if (!selectedRow) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.processedCalls.inspectorEmpty}</p>
  }

  const replayable = selectedRow.status === 'FAILED'
  const hasReplayOutcome = outcome && outcome.callId === selectedRow.callId

  return (
    <div className="space-y-4 text-sm">
      <section>
        <h3 className="mb-2 text-sm font-semibold text-[var(--color-text)]">{uiText.processedCalls.inspectorSelectedTitle}</h3>
        <dl className="space-y-1">
          <div>
            <dt className="text-[var(--color-text-muted)]">{uiText.processedCalls.callIdHeader}</dt>
            <dd className="font-mono">{selectedRow.callId}</dd>
          </div>
          <div>
            <dt className="text-[var(--color-text-muted)]">{uiText.processedCalls.statusHeader}</dt>
            <dd>{formatProcessedCallStatus(selectedRow.status)}</dd>
          </div>
          <div>
            <dt className="text-[var(--color-text-muted)]">{uiText.processedCalls.updatedAtHeader}</dt>
            <dd>{formatProcessedCallDateTime(selectedRow.updatedAt)}</dd>
          </div>
        </dl>
      </section>

      <section>
        <h3 className="mb-2 text-sm font-semibold text-[var(--color-text)]">{uiText.processedCalls.inspectorReplayOutcomeTitle}</h3>
        {hasReplayOutcome ? (
          <p className="text-[var(--color-text-muted)]">
            {formatReplayResultLabel(outcome.result)} ({formatProcessedCallDateTime(outcome.at)})
          </p>
        ) : replayable ? (
          <p className="text-[var(--color-text-muted)]">{uiText.processedCalls.replayTooltip}</p>
        ) : (
          <p className="text-[var(--color-text-muted)]">{uiText.processedCalls.inspectorNotReplayable}</p>
        )}
      </section>
    </div>
  )
}

function notifyReplayOutcome(notify: ReturnType<typeof useNotify>, result: ReplayResult) {
  if (result === 'accepted') {
    notify.success(uiText.processedCalls.replayAcceptedMessage)
    return
  }
  if (result === 'notFound') {
    notify.error(uiText.processedCalls.replayNotFoundMessage)
    return
  }
  if (result === 'notReplayable') {
    notify.warning(uiText.processedCalls.replayNotReplayableMessage)
    return
  }
  notify.error(uiText.processedCalls.replayUnexpectedErrorMessage)
}

function formatReplayResultLabel(result: ReplayResult): string {
  if (result === 'accepted') {
    return uiText.processedCalls.replayAcceptedMessage
  }
  if (result === 'notFound') {
    return uiText.processedCalls.replayNotFoundMessage
  }
  if (result === 'notReplayable') {
    return uiText.processedCalls.replayNotReplayableMessage
  }
  return uiText.processedCalls.replayUnexpectedErrorMessage
}
