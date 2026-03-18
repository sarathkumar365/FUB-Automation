import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from '../app/App'

describe('App routing and shell', () => {
  it('redirects root to webhooks and renders shell', async () => {
    window.history.pushState({}, '', '/')

    render(<App />)

    expect(await screen.findByText('Automation Engine Admin')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Webhooks' })).toBeInTheDocument()
    expect(await screen.findByText('Phase 1 foundation is wired with port-adapter boundaries.')).toBeInTheDocument()
  })
})
