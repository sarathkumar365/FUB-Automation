import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '@app/portsContextValue'
import { useSettingsConfigQuery } from '@modules/settings/data/useSettingsConfigQuery'
import type { SettingsConfig } from '@modules/settings/lib/settingsSchemas'
import type { AppPorts } from '@platform/container'

function sampleConfig(): SettingsConfig {
  return {
    businessHours: { timezone: 'America/Toronto', startHour: 9, endHour: 18, weekdaysOnly: true },
    fubConnection: {
      baseUrl: 'https://api.followupboss.com/v1',
      xSystem: 'AutomationEngineProd',
      apiKey: { present: true },
      xSystemKey: { present: false },
    },
    webhookFubSourceEnabled: true,
  }
}

function makeWrapper(ports: AppPorts) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return function Wrapper({ children }: PropsWithChildren) {
    return (
      <PortsContext.Provider value={ports}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </PortsContext.Provider>
    )
  }
}

describe('useSettingsConfigQuery', () => {
  it('returns the config from settingsPort.getConfig', async () => {
    const getConfig = vi.fn(async () => sampleConfig())
    const ports = { settingsPort: { getConfig } } as unknown as AppPorts

    const { result } = renderHook(() => useSettingsConfigQuery(), { wrapper: makeWrapper(ports) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getConfig).toHaveBeenCalledOnce()
    expect(result.current.data?.businessHours.timezone).toBe('America/Toronto')
    expect(result.current.data?.webhookFubSourceEnabled).toBe(true)
  })

  it('surfaces the error state when the port throws', async () => {
    const getConfig = vi.fn(async () => {
      throw new Error('boom')
    })
    const ports = { settingsPort: { getConfig } } as unknown as AppPorts

    const { result } = renderHook(() => useSettingsConfigQuery(), { wrapper: makeWrapper(ports) })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
