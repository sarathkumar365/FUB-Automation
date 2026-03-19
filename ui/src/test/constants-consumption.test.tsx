import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'

describe('centralized text usage', () => {
  it('renders nav and page titles from uiText constants', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')

    render(<App />)

    expect(await screen.findByText(uiText.app.title)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: uiText.app.nav.webhooks })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: uiText.app.nav.processedCalls })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { name: uiText.webhooks.title })).toHaveLength(2)
  })
})
