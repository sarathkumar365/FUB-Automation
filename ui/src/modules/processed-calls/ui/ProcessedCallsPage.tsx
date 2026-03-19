import { useMemo } from 'react'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { uiText } from '../../../shared/constants/uiText'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'

type ProcessedCallRow = {
  id: string
  outcome: string
  replayStatus: string
  processedAt: string
}

export function ProcessedCallsPage() {
  const rows = useMemo<ProcessedCallRow[]>(() => [], [])
  const columns = useMemo<ColumnDef<ProcessedCallRow>[]>(
    () => [
      {
        key: 'id',
        header: uiText.processedCalls.callIdHeader,
        render: (row) => <span className="font-mono text-xs">{row.id}</span>,
      },
      {
        key: 'outcome',
        header: uiText.processedCalls.outcomeHeader,
        render: (row) => row.outcome,
      },
      {
        key: 'replayStatus',
        header: uiText.processedCalls.replayStatusHeader,
        render: (row) => row.replayStatus,
      },
      {
        key: 'processedAt',
        header: uiText.processedCalls.processedAtHeader,
        render: (row) => row.processedAt,
      },
    ],
    [],
  )

  const inspectorRegion = useMemo(
    () => ({
      title: uiText.processedCalls.inspectorTitle,
      body: <p className="text-sm text-[var(--color-text-muted)]">{uiText.processedCalls.inspectorDescription}</p>,
    }),
    [],
  )

  useShellRegionRegistration({
    panel: null,
    inspector: inspectorRegion,
  })

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.processedCalls.title} subtitle={uiText.processedCalls.subtitle} />
      <PageCard title={uiText.processedCalls.title}>
        <DataTable columns={columns} rows={rows} getRowKey={(row) => row.id} emptyMessage={uiText.processedCalls.emptyMessage} />
      </PageCard>
    </div>
  )
}
