import { z } from 'zod'

const redactableSchema = z.object({
  present: z.boolean(),
  value: z.string().nullable(),
})

// Only the fields the page surfaces; the endpoint also returns fubRetry/callRules/
// extra webhook.* which Zod strips.
export const settingsConfigResponseSchema = z.object({
  businessHours: z.object({
    timezone: z.string(),
    startHour: z.number().int(),
    endHour: z.number().int(),
    weekdaysOnly: z.boolean(),
  }),
  fubConnection: z.object({
    baseUrl: z.string(),
    apiKey: redactableSchema,
    xSystem: z.string(),
    xSystemKey: redactableSchema,
  }),
  webhook: z.object({
    sources: z.object({ enabled: z.boolean() }),
  }),
})

export type SettingsConfigResponse = z.infer<typeof settingsConfigResponseSchema>

export type SecretState = { present: boolean }

// Only fields the backend returns. Unexposed settings (3 of 4 feature flags,
// managed-webhooks) are absent here and rendered "not available" by the UI.
export type SettingsConfig = {
  businessHours: {
    timezone: string
    startHour: number
    endHour: number
    weekdaysOnly: boolean
  }
  fubConnection: {
    baseUrl: string
    xSystem: string
    apiKey: SecretState
    xSystemKey: SecretState
  }
  webhookFubSourceEnabled: boolean
}
