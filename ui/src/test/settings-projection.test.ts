import { describe, expect, it } from 'vitest'
import { projectSettingsConfig } from '../modules/settings/lib/settingsProjection'
import { settingsConfigResponseSchema } from '../modules/settings/lib/settingsSchemas'
import { SETTINGS_SECTIONS, type FieldsSection } from '../modules/settings/lib/settingsSections'

function sampleResponse() {
  return {
    businessHours: { timezone: 'America/Toronto', startHour: 9, endHour: 18, weekdaysOnly: true },
    fubConnection: {
      baseUrl: 'https://api.followupboss.com/v1',
      apiKey: { present: true, value: '***' },
      xSystem: 'AutomationEngineProd',
      xSystemKey: { present: false, value: null },
    },
    webhook: { sources: { enabled: true } },
    // Fields the endpoint returns but the page doesn't surface — must be ignored, not error.
    fubRetry: { maxAttempts: 3, initialDelayMs: 500 },
    callRules: { shortCallThresholdSeconds: 30 },
  }
}

describe('settings projection', () => {
  it('parses the wire response and ignores unsurfaced fields', () => {
    const parsed = settingsConfigResponseSchema.parse(sampleResponse())
    expect(parsed.businessHours.timezone).toBe('America/Toronto')
    expect(parsed.webhook.sources.enabled).toBe(true)
  })

  it('projects only real fields and collapses secrets to presence flags', () => {
    const config = projectSettingsConfig(settingsConfigResponseSchema.parse(sampleResponse()))

    expect(config.businessHours).toEqual({
      timezone: 'America/Toronto',
      startHour: 9,
      endHour: 18,
      weekdaysOnly: true,
    })
    expect(config.fubConnection.baseUrl).toBe('https://api.followupboss.com/v1')
    expect(config.fubConnection.xSystem).toBe('AutomationEngineProd')
    expect(config.fubConnection.apiKey).toEqual({ present: true })
    expect(config.fubConnection.xSystemKey).toEqual({ present: false })
    expect(JSON.stringify(config)).not.toContain('***')
    expect(config.webhookFubSourceEnabled).toBe(true)
  })
})

describe('settings sections metadata', () => {
  const fieldRows = SETTINGS_SECTIONS.filter((s): s is FieldsSection => s.kind === 'fields').flatMap((s) => s.rows)

  it('has unique section ids and the four design sections', () => {
    const ids = SETTINGS_SECTIONS.map((s) => s.id)
    expect(ids).toEqual(['hours', 'flags', 'connections', 'managed'])
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('only the FUB-source flag is backed; the other three flags are "not available"', () => {
    const flags = SETTINGS_SECTIONS.find((s) => s.id === 'flags')
    expect(flags?.kind).toBe('fields')
    const rows = flags && flags.kind === 'fields' ? flags.rows : []
    expect(rows.filter((r) => r.backed).map((r) => r.key)).toEqual(['webhook.sources.fub.enabled'])
    expect(rows.filter((r) => !r.backed)).toHaveLength(3)
  })

  it('every backed field row carries exactly one reader; unbacked rows carry none', () => {
    for (const row of fieldRows) {
      const hasReader = Boolean(row.read) || Boolean(row.readSecretPresent)
      expect(hasReader).toBe(row.backed)
    }
  })

  it('backed readers select live values from a projected config', () => {
    const config = projectSettingsConfig(settingsConfigResponseSchema.parse(sampleResponse()))
    const tz = fieldRows.find((r) => r.key === 'automation.business-hours.timezone')
    const fubFlag = fieldRows.find((r) => r.key === 'webhook.sources.fub.enabled')
    const apiKey = fieldRows.find((r) => r.key === 'fub.api-key')

    expect(tz?.read?.(config)).toBe('America/Toronto')
    expect(fubFlag?.read?.(config)).toBe(true)
    expect(apiKey?.readSecretPresent?.(config)).toBe(true)
  })
})
