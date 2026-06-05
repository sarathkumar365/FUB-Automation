import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ShellRegionsProvider } from '../app/ShellRegionsProvider'
import { useShellRegions } from '../app/useShellRegions'
import { PortsContext } from '../app/portsContextValue'
import { NotifyProvider } from '../shared/notifications/NotifyProvider'
import { SettingsPage } from '../modules/settings/ui/SettingsPage'
import type { SettingsConfig } from '../modules/settings/lib/settingsSchemas'
import type { AppPorts } from '../platform/container'
import { uiText } from '../shared/constants/uiText'

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

// Renders the shell panel region the page publishes, so the section-nav is testable.
function PanelOutlet() {
  return <div data-testid="panel">{useShellRegions().panelContent?.body}</div>
}

function renderSettingsPage(overrides?: { getConfig?: () => Promise<SettingsConfig> }) {
  const getConfig = overrides?.getConfig ?? vi.fn(async () => sampleConfig())
  const ports = { settingsPort: { getConfig } } as unknown as AppPorts
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <ShellRegionsProvider>
          <NotifyProvider>
            <PanelOutlet />
            <SettingsPage />
          </NotifyProvider>
        </ShellRegionsProvider>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return { getConfig }
}

describe('settings page', () => {
  it('renders the header and the default Business hours section from real config', async () => {
    renderSettingsPage()

    expect(await screen.findByRole('heading', { name: uiText.settings.title })).toBeInTheDocument()
    expect(await screen.findByLabelText(uiText.settings.rows.timezone)).toHaveValue('America/Toronto')
  })

  it('fires the coming-soon notice when a control is touched', async () => {
    renderSettingsPage()

    const toggle = await screen.findByRole('switch', { name: uiText.settings.rows.weekdaysOnly })
    fireEvent.click(toggle)

    expect(await screen.findByText(uiText.settings.comingSoon)).toBeInTheDocument()
  })

  it('fires the coming-soon notice on keyboard interaction with a read-only field', async () => {
    renderSettingsPage()

    const startHour = await screen.findByLabelText(uiText.settings.rows.startHour)
    fireEvent.keyDown(startHour, { key: 'ArrowUp' })

    expect(await screen.findByText(uiText.settings.comingSoon)).toBeInTheDocument()
  })

  it('shows the configured timezone even when it is outside a curated list', async () => {
    renderSettingsPage({
      getConfig: vi.fn(async () => {
        const base = sampleConfig()
        return { ...base, businessHours: { ...base.businessHours, timezone: 'Pacific/Auckland' } }
      }),
    })

    expect(await screen.findByLabelText(uiText.settings.rows.timezone)).toHaveValue('Pacific/Auckland')
  })

  it('shows the one backed flag and "not available" for the unexposed ones', async () => {
    renderSettingsPage()

    fireEvent.click(await screen.findByRole('button', { name: uiText.settings.sections.flags.title }))

    expect(await screen.findByRole('switch', { name: uiText.settings.rows.fubSourceEnabled })).toBeInTheDocument()
    expect(screen.getAllByText(uiText.settings.notAvailable).length).toBeGreaterThanOrEqual(3)
  })

  it('shows the managed-webhooks not-available state with a Sync now action', async () => {
    renderSettingsPage()

    fireEvent.click(await screen.findByRole('button', { name: uiText.settings.sections.managed.title }))

    expect(await screen.findByText(uiText.settings.sections.managed.empty)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: new RegExp(uiText.settings.sections.managed.syncNow) })).toBeInTheDocument()
  })

  it('renders the loading state while the config query is pending', () => {
    renderSettingsPage({
      getConfig: vi.fn(
        () =>
          new Promise<SettingsConfig>(() => {
            /* pending */
          }),
      ),
    })

    expect(screen.getByText(uiText.states.loadingTitle)).toBeInTheDocument()
  })

  it('renders the error state when the config query fails', async () => {
    renderSettingsPage({
      getConfig: vi.fn(async () => {
        throw new Error('config failed')
      }),
    })

    expect(await screen.findByText(uiText.states.errorTitle)).toBeInTheDocument()
  })
})
