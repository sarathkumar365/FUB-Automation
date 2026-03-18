import { PageCard } from '../../../shared/ui/PageCard'

export function ProcessedCallsPage() {
  return (
    <PageCard
      title="Processed Calls"
      subtitle="Module scaffold is ready. List and replay behavior will be added in the next implementation slice."
    >
      <p className="text-sm text-slate-700">Planned endpoints: GET /admin/processed-calls, POST /admin/processed-calls/{'{callId}'}/replay</p>
    </PageCard>
  )
}
