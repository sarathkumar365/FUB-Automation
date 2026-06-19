import type { SettingsConfig, SettingsConfigResponse } from '../../contracts/settingsSchemas'

// Secrets collapse to presence flags; nothing absent is invented.
export function projectSettingsConfig(response: SettingsConfigResponse): SettingsConfig {
  return {
    businessHours: {
      timezone: response.businessHours.timezone,
      startHour: response.businessHours.startHour,
      endHour: response.businessHours.endHour,
      weekdaysOnly: response.businessHours.weekdaysOnly,
    },
    fubConnection: {
      baseUrl: response.fubConnection.baseUrl,
      xSystem: response.fubConnection.xSystem,
      apiKey: { present: response.fubConnection.apiKey.present },
      xSystemKey: { present: response.fubConnection.xSystemKey.present },
    },
    webhookFubSourceEnabled: response.webhook.sources.enabled,
  }
}
