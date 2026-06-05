import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import App from '../app/App'
import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'
import { clearMockAdminToken, seedMockAdminToken } from './support/authTestHelpers'

describe('Option 1 shell regions', () => {
  beforeEach(() => {
    seedMockAdminToken()
  })

  afterEach(() => {
    clearMockAdminToken()
  })


  it('renders route-level inspector descriptors for webhooks without panel placeholder copy', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')

    render(<App />)

    expect(screen.queryByText(uiText.app.shell.panelTitle)).not.toBeInTheDocument()
    expect(screen.getByText(uiText.webhooks.inspectorTitle)).toBeInTheDocument()
    expect(screen.getByText(uiText.webhooks.inspectorEmpty)).toBeInTheDocument()
  })

  it('redirects a guarded route to the full-page session-disabled screen (no shell chrome)', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')
    window.sessionStorage.setItem('admin-ui-enabled', 'false')

    render(<App />)

    expect(await screen.findByRole('heading', { name: uiText.session.title })).toBeInTheDocument()
    expect(screen.getByText(uiText.session.body)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: uiText.session.returnToSignIn })).toHaveAttribute(
      'href',
      routes.login,
    )
    // Full-page: rendered outside AppShell, so no four-region chrome.
    expect(screen.queryByLabelText(uiText.app.shell.railAriaLabel)).not.toBeInTheDocument()
    window.sessionStorage.removeItem('admin-ui-enabled')
  })

  it('shows responsive toggle controls for panel and inspector', async () => {
    window.history.pushState({}, '', '/admin-ui/processed-calls')

    render(<App />)

    expect(await screen.findByRole('button', { name: uiText.app.shell.openPanel })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: uiText.app.shell.openInspector })).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.inspectorAriaLabel).className).toContain('lg:block')
  })

  it('keeps inspector in a dedicated desktop sibling region while content uses bounded scrolling', async () => {
    window.history.pushState({}, '', '/admin-ui/processed-calls')

    render(<App />)

    const inspector = await screen.findByLabelText(uiText.app.shell.inspectorAriaLabel)
    const content = screen.getByLabelText(uiText.app.shell.contentAriaLabel)

    expect(inspector.className).toContain('lg:block')
    expect(inspector.className).toContain('lg:overflow-y-auto')
    expect(content.className).toContain('overflow-auto')
    expect(content.className).toContain('min-h-0')
  })
})
