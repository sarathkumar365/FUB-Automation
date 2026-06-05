import { Button, EmptyState, PageCard, RefreshIcon } from '../../../shared/ui'
import { uiText } from '../../../shared/constants/uiText'

// Managed-webhooks has no read endpoint yet — show the section + an explicit
// "not available" state (no mock rows). "Sync now" defers to the coming-soon notice.
export function ManagedWebhooksCard({ onSync }: { onSync: () => void }) {
  const t = uiText.settings.sections.managed
  return (
    <PageCard title={t.title}>
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-[var(--color-text-muted)]">{t.note}</p>
        <Button size="sm" variant="outline" onClick={onSync}>
          <RefreshIcon className="mr-1.5 h-3.5 w-3.5" />
          {t.syncNow}
        </Button>
      </div>
      <div className="mt-4">
        <EmptyState message={t.empty} />
      </div>
    </PageCard>
  )
}
