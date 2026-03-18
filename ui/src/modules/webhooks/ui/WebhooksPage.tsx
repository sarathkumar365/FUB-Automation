import { PageCard } from '../../../shared/ui/PageCard'
import { useWebhooksPreview } from '../data/useWebhooksPreview'

export function WebhooksPage() {
  const preview = useWebhooksPreview()

  return (
    <div className="space-y-4">
      <PageCard title="Webhooks" subtitle="Phase 1 foundation is wired with port-adapter boundaries.">
        <ul className="space-y-2 text-sm text-slate-700">
          <li>
            <span className="font-medium">History endpoint:</span> {preview.historyPath}
          </li>
          <li>
            <span className="font-medium">Detail endpoint:</span> {preview.detailPathTemplate}
          </li>
          <li>
            <span className="font-medium">Live stream endpoint:</span> {preview.streamPath}
          </li>
        </ul>
      </PageCard>
    </div>
  )
}
