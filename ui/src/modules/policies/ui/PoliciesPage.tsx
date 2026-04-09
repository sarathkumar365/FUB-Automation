import { PageHeader } from '../../../shared/ui/PageHeader'
import { uiText } from '../../../shared/constants/uiText'

export function PoliciesPage() {
  return (
    <div>
      <PageHeader title={uiText.policies.title} subtitle={uiText.policies.subtitle} />
      <p className="mt-4 text-sm text-gray-500">Policies page — Stage 2 will wire the Runs tab here.</p>
    </div>
  )
}
