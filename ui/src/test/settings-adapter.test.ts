import { describe, expect, it, vi } from 'vitest'
import type { ZodType } from 'zod'
import type { HttpJsonClient } from '../platform/adapters/http/httpJsonClient'
import { HttpSettingsAdapter } from '../platform/adapters/http/httpSettingsAdapter'

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
  }
}

describe('HttpSettingsAdapter', () => {
  it('reads /admin/settings/config and returns the projected config', async () => {
    const get = vi.fn((path: string, schema: ZodType) => {
      expect(path).toBe('/admin/settings/config')
      return Promise.resolve(schema.parse(sampleResponse()))
    })
    const adapter = new HttpSettingsAdapter({ get } as unknown as HttpJsonClient)

    const config = await adapter.getConfig()

    expect(config.businessHours.timezone).toBe('America/Toronto')
    expect(config.fubConnection.apiKey).toEqual({ present: true })
    expect(config.fubConnection.xSystemKey).toEqual({ present: false })
    expect(config.webhookFubSourceEnabled).toBe(true)
  })
})
