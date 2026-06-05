import { useMemo, useState } from 'react'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { uiText } from '../../../shared/constants/uiText'
import { useNotify } from '../../../shared/notifications/useNotify'
import { ErrorState, LoadingState, PageCard, PageHeader } from '../../../shared/ui'
import { useSettingsConfigQuery } from '../data/useSettingsConfigQuery'
import type { SettingsConfig } from '../lib/settingsSchemas'
import { SETTINGS_SECTIONS, type FieldsSection } from '../lib/settingsSections'
import { ManagedWebhooksCard } from './ManagedWebhooksCard'
import { SettingRow } from './SettingRow'

export function SettingsPage() {
  const t = uiText.settings
  const notify = useNotify()
  const query = useSettingsConfigQuery()
  const config = query.data

  const [sectionId, setSectionId] = useState<string>(SETTINGS_SECTIONS[0].id)
  const onEdit = () => notify.info(t.comingSoon)

  const panelRegion = useMemo(
    () => ({
      title: t.panelTitle,
      body: (
        <nav className="flex flex-col gap-0.5" aria-label={t.panelTitle}>
          {SETTINGS_SECTIONS.map((section) => {
            const active = section.id === sectionId
            return (
              <button
                key={section.id}
                type="button"
                onClick={() => setSectionId(section.id)}
                aria-current={active ? 'true' : undefined}
                className={[
                  'rounded-md px-2.5 py-2 text-left text-[13px] transition-colors',
                  active
                    ? 'bg-[var(--color-brand-soft)] font-semibold text-[var(--color-brand)]'
                    : 'font-medium text-[var(--color-text)] hover:bg-[var(--color-surface-alt)]',
                ].join(' ')}
              >
                {section.title}
              </button>
            )
          })}
        </nav>
      ),
    }),
    [sectionId, t.panelTitle],
  )

  useShellRegionRegistration({ panel: panelRegion, inspector: null })

  if (query.isPending) {
    return (
      <div className="space-y-4">
        <PageHeader title={t.title} subtitle={t.subtitle} />
        <LoadingState />
      </div>
    )
  }

  if (query.isError || !config) {
    return (
      <div className="space-y-4">
        <PageHeader title={t.title} subtitle={t.subtitle} />
        <ErrorState onRetry={() => void query.refetch()} />
      </div>
    )
  }

  const section = SETTINGS_SECTIONS.find((entry) => entry.id === sectionId) ?? SETTINGS_SECTIONS[0]

  return (
    <div className="flex max-w-[720px] flex-col gap-4">
      <PageHeader title={t.title} subtitle={t.subtitle} />
      {section.kind === 'managed' ? (
        <ManagedWebhooksCard onSync={onEdit} />
      ) : (
        <FieldsSectionCard section={section} config={config} onEdit={onEdit} />
      )}
    </div>
  )
}

function FieldsSectionCard({
  section,
  config,
  onEdit,
}: {
  section: FieldsSection
  config: SettingsConfig
  onEdit: () => void
}) {
  const t = uiText.settings
  return (
    <PageCard title={section.title}>
      <div className="flex items-center justify-between gap-3">
        <p className="max-w-[520px] text-sm text-[var(--color-text-muted)]">{section.note}</p>
        {section.connected ? (
          <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-[var(--color-status-ok-bg)] px-2.5 py-1 text-xs font-semibold text-[var(--color-status-ok)]">
            <span className="h-[7px] w-[7px] rounded-full bg-[var(--color-live)]" />
            {t.connectedBadge}
          </span>
        ) : null}
      </div>
      <div className="mt-1.5">
        {section.rows.map((row, index) => (
          <SettingRow
            key={row.key}
            row={row}
            config={config}
            onEdit={onEdit}
            isLast={index === section.rows.length - 1}
          />
        ))}
      </div>
    </PageCard>
  )
}
