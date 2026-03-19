import { useMemo } from 'react'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { useWebhookStream } from '../../../platform/stream/useWebhookStream'
import { uiText } from '../../../shared/constants/uiText'
import { DataTable, type ColumnDef } from '../../../shared/ui/DataTable'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useWebhooksPreview } from '../data/useWebhooksPreview'
import { type WebhookPreviewRow, toWebhookPreviewRows } from '../lib/webhookPreviewRows'
import type { WebhookStreamEvent } from '../../../shared/types/webhook'

export function WebhooksPage() {
  const preview = useWebhooksPreview()
  const { events, state } = useWebhookStream()

  const rows = useMemo(() => toWebhookPreviewRows(preview), [preview])
  const columns = useMemo<ColumnDef<WebhookPreviewRow>[]>(
    () => [
      {
        key: 'label',
        header: uiText.webhooks.previewFieldHeader,
        render: (row) => <span className="font-medium">{row.label}</span>,
      },
      {
        key: 'value',
        header: uiText.webhooks.previewValueHeader,
        render: (row) => <code className="font-mono text-xs">{row.value}</code>,
      },
    ],
    [],
  )
  const streamColumns = useMemo<ColumnDef<WebhookStreamEvent>[]>(
    () => [
      {
        key: 'id',
        header: uiText.webhooks.streamIdHeader,
        render: (row) => <span className="font-mono text-xs">{row.id}</span>,
      },
      {
        key: 'eventType',
        header: uiText.webhooks.streamEventTypeHeader,
        render: (row) => row.eventType,
      },
      {
        key: 'status',
        header: uiText.webhooks.streamStatusHeader,
        render: (row) => <StatusBadge label={row.status} tone="info" />,
      },
      {
        key: 'source',
        header: uiText.webhooks.streamSourceHeader,
        render: (row) => row.source,
      },
      {
        key: 'receivedAt',
        header: uiText.webhooks.streamReceivedAtHeader,
        render: (row) => row.receivedAt,
      },
    ],
    [],
  )
  const inspectorRegion = useMemo(
    () => ({
      title: uiText.webhooks.inspectorTitle,
      body: <p className="text-sm text-[var(--color-text-muted)]">{uiText.webhooks.inspectorDescription}</p>,
    }),
    [],
  )

  useShellRegionRegistration({
    panel: null,
    inspector: inspectorRegion,
  })

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.webhooks.title} subtitle={uiText.webhooks.subtitle} />
      <PageCard title={uiText.webhooks.title}>
        <DataTable columns={columns} rows={rows} getRowKey={(row) => row.key} />
      </PageCard>
      <PageCard title={uiText.webhooks.streamTitle} subtitle={`${uiText.webhooks.streamStateLabel}: ${state}`}>
        <DataTable
          columns={streamColumns}
          rows={events}
          getRowKey={(row) => row.id}
          emptyMessage={uiText.webhooks.streamEmptyMessage}
        />
      </PageCard>
    </div>
  )
}
