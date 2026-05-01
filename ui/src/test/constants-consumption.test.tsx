import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'
import { clearMockAdminToken, seedMockAdminToken } from './support/authTestHelpers'

describe('centralized text usage', () => {
  beforeEach(() => {
    seedMockAdminToken()
  })

  afterEach(() => {
    clearMockAdminToken()
  })


  it('renders nav and page titles from uiText constants', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')

    render(<App />)

    expect(await screen.findByText(uiText.app.title)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: uiText.app.nav.webhooks })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: uiText.app.nav.processedCalls })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: uiText.webhooks.title })).toBeInTheDocument()
  })
})
