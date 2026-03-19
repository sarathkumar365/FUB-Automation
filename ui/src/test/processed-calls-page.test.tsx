import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'

describe('Processed Calls page', () => {
  it('renders processed calls table scaffold with centralized empty message', async () => {
    window.history.pushState({}, '', '/admin-ui/webhooks')
    const user = userEvent.setup()

    render(<App />)

    await user.click(screen.getAllByRole('link', { name: uiText.app.nav.processedCalls })[0])

    expect(await screen.findAllByRole('heading', { name: uiText.processedCalls.title })).toHaveLength(2)
    expect(screen.getByText(uiText.processedCalls.emptyMessage)).toBeInTheDocument()
    expect(screen.queryByText('Planned endpoints')).not.toBeInTheDocument()
  })
})
