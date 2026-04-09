import { useMemo } from 'react'
import { uiText } from '../../../shared/constants/uiText'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { Button } from '../../../shared/ui/button'
import { NextIcon, PrevIcon } from '../../../shared/ui/icons'
import { PageCard } from '../../../shared/ui/PageCard'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { PolicyExecutionRunListItem } from '../lib/policySchemas'
import { formatPolicyLabel, formatRelativeTime, runStatusLabel, runStatusTone } from '../lib/policiesDisplay'

type RunsTabProps = {
  rows: PolicyExecutionRunListItem[]
  isPending: boolean
  isError: boolean
  selectedRunId: number | undefined
  nextCursor: string | null
  hasPrev: boolean
  onRowClick: (row: PolicyExecutionRunListItem) => void
  onNext: () => void
  onPrev: () => void
}

export function RunsTab({
  rows,
  isPending,
  isError,
  selectedRunId,
  nextCursor,
  hasPrev,
  onRowClick,
  onNext,
  onPrev,
}: RunsTabProps) {
  const columns = useMemo<ColumnDef<PolicyExecutionRunListItem>[]>(
    () => [
      {
        key: 'id',
        header: uiText.policies.runsIdHeader,
        render: (row) => <span className="font-mono text-xs">{row.id}</span>,
      },
      {
        key: 'lead',
        header: uiText.policies.runsLeadHeader,
        render: (row) => (
          <span className="font-mono text-xs">{row.sourceLeadId ?? '-'}</span>
        ),
      },
      {
        key: 'policy',
        header: uiText.policies.runsPolicyHeader,
        render: (row) => (
          <span className="text-xs">{formatPolicyLabel(row.policyKey, row.policyVersion)}</span>
        ),
      },
      {
        key: 'status',
        header: uiText.policies.runsStatusHeader,
        render: (row) => (
          <StatusBadge label={runStatusLabel(row.status)} tone={runStatusTone(row.status)} />
        ),
      },
      {
        key: 'when',
        header: uiText.policies.runsWhenHeader,
        render: (row) => (
          <span className="font-mono text-xs text-[var(--color-text-muted)]">
            {formatRelativeTime(row.createdAt)}
          </span>
        ),
      },
    ],
    [],
  )

  return (
    <PageCard title={uiText.policies.runsTableTitle}>
      {isError ? (
        <ErrorState message={uiText.states.errorMessage} />
      ) : isPending ? (
        <LoadingState />
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={rows}
            getRowKey={(row) => row.id}
            getRowAriaLabel={(row) => `${uiText.policies.runsRowAriaPrefix} ${row.id}`}
            onRowClick={onRowClick}
            selectedRowKey={selectedRunId ?? null}
            emptyMessage={uiText.policies.runsEmptyMessage}
          />
          <div className="mt-3 flex items-center justify-end gap-2">
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={onPrev}
              disabled={!hasPrev}
              aria-label={uiText.policies.paginationPrev}
            >
              <PrevIcon className="mr-1.5" />
              {uiText.policies.paginationPrev}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={onNext}
              disabled={!nextCursor}
              aria-label={uiText.policies.paginationNext}
            >
              <NextIcon className="mr-1.5" />
              {nextCursor ? uiText.policies.paginationNext : uiText.policies.paginationNoMore}
            </Button>
          </div>
        </>
      )}
    </PageCard>
  )
}
