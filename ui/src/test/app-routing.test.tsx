import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'

describe('App routing and shell', () => {
  it('redirects root to webhooks and renders shell', async () => {
    window.history.pushState({}, '', '/')

    render(<App />)

    expect(await screen.findByText('Automation Engine Admin')).toBeInTheDocument()
    expect(await screen.findAllByRole('heading', { name: 'Webhooks' })).toHaveLength(2)
    expect(await screen.findByText(uiText.webhooks.subtitle)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.railAriaLabel)).toBeInTheDocument()
    expect(screen.queryByLabelText(uiText.app.shell.panelAriaLabel)).not.toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.contentAriaLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.inspectorAriaLabel)).toBeInTheDocument()
  })
})
