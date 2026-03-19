import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'

describe('Option 1 shell regions', () => {
  it('renders route-level inspector descriptors for webhooks without panel placeholder copy', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')

    render(<App />)

    expect(screen.queryByText(uiText.app.shell.panelTitle)).not.toBeInTheDocument()
    expect(screen.getByText(uiText.webhooks.inspectorTitle)).toBeInTheDocument()
    expect(screen.getByText(uiText.webhooks.inspectorDescription)).toBeInTheDocument()
  })

  it('renders session-disabled route inside the same shell', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')
    window.sessionStorage.setItem('admin-ui-enabled', 'false')

    render(<App />)

    expect(await screen.findAllByText(uiText.session.disabledMessage)).toHaveLength(2)
    expect(screen.getByLabelText(uiText.app.shell.railAriaLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.panelAriaLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.inspectorAriaLabel)).toBeInTheDocument()
    window.sessionStorage.removeItem('admin-ui-enabled')
  })

  it('shows responsive toggle controls for panel and inspector', async () => {
    window.history.pushState({}, '', '/admin-ui/processed-calls')

    render(<App />)

    expect(await screen.findByRole('button', { name: uiText.app.shell.openPanel })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: uiText.app.shell.openInspector })).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.inspectorAriaLabel).className).toContain('lg:block')
  })
})
