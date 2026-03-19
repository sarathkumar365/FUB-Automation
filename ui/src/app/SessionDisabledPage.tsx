import { useMemo } from 'react'
import { useShellRegionRegistration } from './useShellRegionRegistration'
import { uiText } from '../shared/constants/uiText'
import { EmptyState } from '../shared/ui/EmptyState'
import { PageCard } from '../shared/ui/PageCard'
import { PageHeader } from '../shared/ui/PageHeader'

export function SessionDisabledPage() {
  const panelRegion = useMemo(
    () => ({
      title: uiText.app.shell.panelTitle,
      body: <p>{uiText.session.disabledPanelNote}</p>,
    }),
    [],
  )
  const inspectorRegion = useMemo(
    () => ({
      title: uiText.app.shell.inspectorTitle,
      body: <p className="text-sm text-[var(--color-text-muted)]">{uiText.app.shell.inspectorFallback}</p>,
    }),
    [],
  )

  useShellRegionRegistration({
    panel: panelRegion,
    inspector: inspectorRegion,
  })

  return (
    <div className="space-y-4">
      <PageHeader title={uiText.app.title} subtitle={uiText.session.disabledMessage} />
      <PageCard title={uiText.notifications.warningTitle}>
        <EmptyState message={uiText.session.disabledMessage} />
      </PageCard>
    </div>
  )
}
