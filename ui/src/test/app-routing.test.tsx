import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'

describe('App routing and shell', () => {
  it('renders landing page at root', async () => {
    window.history.pushState({}, '', '/')

    render(<App />)

    expect(await screen.findByText(uiText.landing.kicker)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: uiText.landing.title })).toBeInTheDocument()
    expect(screen.getByText(uiText.landing.subtitle)).toBeInTheDocument()
  })

  it('keeps admin-ui shell routes unchanged', async () => {
    window.history.pushState({}, '', '/admin-ui')

    render(<App />)

    expect(await screen.findByText('Automation Engine Admin')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Webhooks' })).toBeInTheDocument()
    expect(await screen.findByText(uiText.webhooks.subtitle)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.railAriaLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.contentAriaLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.app.shell.inspectorAriaLabel)).toBeInTheDocument()
  })

  it('navigates to landing when clicking rail brand icon', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', '/admin-ui/webhooks')

    render(<App />)

    await user.click(await screen.findByRole('link', { name: uiText.app.nav.home }))

    expect(await screen.findByRole('heading', { name: uiText.landing.title })).toBeInTheDocument()
  })
})
