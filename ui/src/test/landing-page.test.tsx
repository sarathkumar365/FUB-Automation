import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from '../app/App'
import { uiText } from '../shared/constants/uiText'

describe('Landing page', () => {
  it('renders five milestones and key labels', async () => {
    window.history.pushState({}, '', '/')

    render(<App />)

    expect(await screen.findByRole('heading', { name: uiText.landing.title })).toBeInTheDocument()
    expect(screen.getAllByTestId('landing-milestone')).toHaveLength(5)
    expect(screen.getByRole('heading', { name: uiText.landing.milestones.oneTitle })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: uiText.landing.milestones.fiveTitle })).toBeInTheDocument()
  })

  it('navigates to webhooks from primary CTA', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', '/')

    render(<App />)

    await user.click(await screen.findByRole('button', { name: uiText.landing.primaryAction }))

    expect(await screen.findByRole('heading', { name: uiText.webhooks.title })).toBeInTheDocument()
  })

  it('navigates to processed calls from secondary CTA', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', '/')

    render(<App />)

    await user.click(await screen.findByRole('button', { name: uiText.landing.secondaryAction }))

    expect(await screen.findByRole('heading', { name: uiText.processedCalls.title })).toBeInTheDocument()
  })

  it('applies explicit typography classes for finalized visual weight', async () => {
    window.history.pushState({}, '', '/')

    render(<App />)

    const title = await screen.findByRole('heading', { name: uiText.landing.title })
    const milestoneTitle = screen.getByRole('heading', { name: uiText.landing.milestones.oneTitle })

    expect(title).toHaveClass('landing-title')
    expect(milestoneTitle).toHaveClass('landing-milestone-title')
  })

  it('uses centralized aria labels for landing sections', async () => {
    window.history.pushState({}, '', '/')

    render(<App />)

    expect(await screen.findByLabelText(uiText.landing.workspaceAriaLabel)).toBeInTheDocument()
    expect(screen.getByLabelText(uiText.landing.timelineAriaLabel)).toBeInTheDocument()
    expect(screen.getByRole('img', { name: uiText.landing.timelinePathAriaLabel })).toBeInTheDocument()
  })
})
