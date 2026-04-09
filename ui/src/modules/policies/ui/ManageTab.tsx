import { useMemo } from 'react'
import { uiText } from '../../../shared/constants/uiText'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { PolicyResponse } from '../lib/policySchemas'
import { policyStatusLabel, policyStatusTone } from '../lib/policiesDisplay'

type ManageTabProps = {
  rows: PolicyResponse[]
  isPending: boolean
  isError: boolean
  selectedPolicyId: number | undefined
  onRowClick: (row: PolicyResponse) => void
}

export function ManageTab({
  rows,
  isPending,
  isError,
  selectedPolicyId,
  onRowClick,
}: ManageTabProps) {
  const columns = useMemo<ColumnDef<PolicyResponse>[]>(
    () => [
      {
        key: 'policyKey',
        header: uiText.policies.managePolicyKeyHeader,
        render: (row) => <span className="font-mono text-xs">{row.policyKey}</span>,
      },
      {
        key: 'version',
        header: uiText.policies.manageVersionHeader,
        render: (row) => <span className="font-mono text-xs">v{row.version}</span>,
      },
      {
        key: 'status',
        header: uiText.policies.manageStatusHeader,
        render: (row) => (
          <StatusBadge label={policyStatusLabel(row.status)} tone={policyStatusTone(row.status)} />
        ),
      },
      {
        key: 'enabled',
        header: uiText.policies.manageEnabledHeader,
        render: (row) => <span className="text-xs">{row.enabled ? 'Yes' : 'No'}</span>,
      },
    ],
    [],
  )

  return (
    <PageCard title={uiText.policies.manageTableTitle}>
      {isError ? (
        <ErrorState message={uiText.states.errorMessage} />
      ) : isPending ? (
        <LoadingState />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          getRowKey={(row) => row.id}
          getRowAriaLabel={(row) => `${uiText.policies.managePolicyRowAriaPrefix} ${row.policyKey} v${row.version}`}
          onRowClick={onRowClick}
          selectedRowKey={selectedPolicyId ?? null}
          emptyMessage={uiText.policies.manageEmptyMessage}
        />
      )}
    </PageCard>
  )
}
