import { uiText } from '@shared/constants/uiText'
import type { SettingsConfig } from './settingsSchemas'

// Full IANA zone list (native, no dependency) so any backend-configured
// timezone is always selectable/displayable — not a curated subset.
const TIMEZONES = Intl.supportedValuesOf('timeZone')

export type SettingValue = string | number | boolean

export type SettingControl =
  | { kind: 'toggle' }
  | { kind: 'number'; unit?: string }
  | { kind: 'select'; options: readonly string[] }
  | { kind: 'text' }
  | { kind: 'secret' }

// backed: false → endpoint doesn't expose it yet (UI shows "not available").
// read / readSecretPresent select the live value and exist iff backed.
export type SettingRowDef = {
  key: string
  label: string
  description?: string
  control: SettingControl
  backed: boolean
  read?: (config: SettingsConfig) => SettingValue
  readSecretPresent?: (config: SettingsConfig) => boolean
}

export type FieldsSection = {
  id: string
  kind: 'fields'
  title: string
  note: string
  connected?: boolean
  rows: SettingRowDef[]
}

export type ManagedSection = {
  id: string
  kind: 'managed'
  title: string
  note: string
}

export type SettingsSection = FieldsSection | ManagedSection

const t = uiText.settings

export const SETTINGS_SECTIONS: readonly SettingsSection[] = [
  {
    id: 'hours',
    kind: 'fields',
    title: t.sections.hours.title,
    note: t.sections.hours.note,
    rows: [
      {
        key: 'automation.business-hours.timezone',
        label: t.rows.timezone,
        control: { kind: 'select', options: TIMEZONES },
        backed: true,
        read: (c) => c.businessHours.timezone,
      },
      {
        key: 'automation.business-hours.start-hour',
        label: t.rows.startHour,
        description: t.rows.startHourDesc,
        control: { kind: 'number', unit: t.hoursUnit },
        backed: true,
        read: (c) => c.businessHours.startHour,
      },
      {
        key: 'automation.business-hours.end-hour',
        label: t.rows.endHour,
        description: t.rows.endHourDesc,
        control: { kind: 'number', unit: t.hoursUnit },
        backed: true,
        read: (c) => c.businessHours.endHour,
      },
      {
        key: 'automation.business-hours.weekdays-only',
        label: t.rows.weekdaysOnly,
        description: t.rows.weekdaysOnlyDesc,
        control: { kind: 'toggle' },
        backed: true,
        read: (c) => c.businessHours.weekdaysOnly,
      },
    ],
  },
  {
    id: 'flags',
    kind: 'fields',
    title: t.sections.flags.title,
    note: t.sections.flags.note,
    rows: [
      {
        key: 'webhook.sources.fub.enabled',
        label: t.rows.fubSourceEnabled,
        description: t.rows.fubSourceEnabledDesc,
        control: { kind: 'toggle' },
        backed: true,
        read: (c) => c.webhookFubSourceEnabled,
      },
      // Not returned by GET /admin/settings/config — rendered "not available yet".
      {
        key: 'engine.write.emit-events',
        label: t.rows.emitEngineWriteEvents,
        description: t.rows.emitEngineWriteEventsDesc,
        control: { kind: 'toggle' },
        backed: false,
      },
      {
        key: 'workflow.worker.enabled',
        label: t.rows.workerEnabled,
        description: t.rows.workerEnabledDesc,
        control: { kind: 'toggle' },
        backed: false,
      },
      {
        key: 'workflow.worker.stale-processing-enabled',
        label: t.rows.staleRequeue,
        description: t.rows.staleRequeueDesc,
        control: { kind: 'toggle' },
        backed: false,
      },
    ],
  },
  {
    id: 'connections',
    kind: 'fields',
    title: t.sections.connections.title,
    note: t.sections.connections.note,
    connected: true,
    rows: [
      {
        key: 'fub.base-url',
        label: t.rows.fubBaseUrl,
        control: { kind: 'text' },
        backed: true,
        read: (c) => c.fubConnection.baseUrl,
      },
      {
        key: 'fub.x-system',
        label: t.rows.fubXSystem,
        control: { kind: 'text' },
        backed: true,
        read: (c) => c.fubConnection.xSystem,
      },
      {
        key: 'fub.api-key',
        label: t.rows.fubApiKey,
        control: { kind: 'secret' },
        backed: true,
        readSecretPresent: (c) => c.fubConnection.apiKey.present,
      },
      {
        key: 'fub.x-system-key',
        label: t.rows.fubXSystemKey,
        control: { kind: 'secret' },
        backed: true,
        readSecretPresent: (c) => c.fubConnection.xSystemKey.present,
      },
    ],
  },
  {
    id: 'managed',
    kind: 'managed',
    title: t.sections.managed.title,
    note: t.sections.managed.note,
  },
]
