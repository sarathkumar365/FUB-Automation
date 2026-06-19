import type { KeyboardEvent } from 'react'
import { Input, Select, Toggle } from '@shared/ui'
import { uiText } from '@shared/constants/uiText'
import type { SettingsConfig } from '../lib/settingsSchemas'
import type { SettingRowDef } from '../lib/settingsSections'

type SettingRowProps = {
  row: SettingRowDef
  config: SettingsConfig
  onEdit: () => void
  isLast: boolean
}

export function SettingRow({ row, config, onEdit, isLast }: SettingRowProps) {
  return (
    <div
      className={[
        'flex items-center justify-between gap-6 py-4',
        isLast ? '' : 'border-b border-[color-mix(in_srgb,var(--color-border),transparent_40%)]',
      ].join(' ')}
    >
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-sm font-semibold text-[var(--color-text)]">{row.label}</span>
        {row.description ? (
          <span className="max-w-[460px] text-xs text-[var(--color-text-muted)]">{row.description}</span>
        ) : null}
        <span className="font-mono text-[11px] text-[var(--color-text-muted)]">{row.key}</span>
      </div>
      <div className="shrink-0">
        <RowControl row={row} config={config} onEdit={onEdit} />
      </div>
    </div>
  )
}

function RowControl({ row, config, onEdit }: Omit<SettingRowProps, 'isLast'>) {
  const t = uiText.settings

  // Not exposed by the read endpoint yet — show status, never a fabricated value.
  if (!row.backed) {
    return <span className="text-xs text-[var(--color-text-muted)]">{t.notAvailable}</span>
  }

  // Read-only display; surface the coming-soon notice on any edit attempt via
  // mouse and keyboard, so it stays keyboard-accessible.
  const editTriggers = {
    readOnly: true,
    onMouseDown: () => onEdit(),
    onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => {
      if (event.key === 'Tab') return
      event.preventDefault()
      onEdit()
    },
  }

  const control = row.control

  if (control.kind === 'toggle') {
    const checked = Boolean(row.read?.(config))
    return (
      <div className="flex items-center gap-2.5">
        <span
          className={[
            'text-[11px] font-bold',
            checked ? 'text-[var(--color-status-ok)]' : 'text-[var(--color-text-muted)]',
          ].join(' ')}
        >
          {checked ? t.toggleOn : t.toggleOff}
        </span>
        <Toggle checked={checked} onCheckedChange={() => onEdit()} ariaLabel={row.label} />
      </div>
    )
  }

  if (control.kind === 'select') {
    return (
      <Select
        value={String(row.read?.(config) ?? '')}
        onChange={() => onEdit()}
        aria-label={row.label}
        className="w-[210px]"
      >
        {control.options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </Select>
    )
  }

  if (control.kind === 'number') {
    return (
      <div className="flex items-center gap-2">
        <Input
          type="number"
          value={String(row.read?.(config) ?? '')}
          {...editTriggers}
          aria-label={row.label}
          className="h-9 w-[84px]"
        />
        {control.unit ? <span className="text-xs text-[var(--color-text-muted)]">{control.unit}</span> : null}
      </div>
    )
  }

  if (control.kind === 'secret') {
    const present = row.readSecretPresent?.(config) ?? false
    return (
      <span className="text-xs text-[var(--color-text-muted)]">
        {present ? t.secretConfigured : t.secretNotConfigured}
      </span>
    )
  }

  return (
    <Input
      value={String(row.read?.(config) ?? '')}
      {...editTriggers}
      aria-label={row.label}
      className="h-9 w-[280px]"
    />
  )
}
