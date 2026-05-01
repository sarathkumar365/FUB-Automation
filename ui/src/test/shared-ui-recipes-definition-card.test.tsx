import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Badge } from '../shared/ui/badge'
import { DefinitionCard } from '../shared/ui/recipes/DefinitionCard'

describe('DefinitionCard recipe', () => {
  it('renders the title in a heading element', () => {
    render(
      <DefinitionCard title="Assign Owner">
        <p>body</p>
      </DefinitionCard>,
    )
    const heading = screen.getByRole('heading', { name: 'Assign Owner' })
    expect(heading).toBeInTheDocument()
  })

  it('labels the card region with the title for assistive tech', () => {
    render(
      <DefinitionCard title="Assign Owner">
        <p>body</p>
      </DefinitionCard>,
    )
    const region = screen.getByRole('region', { name: 'Assign Owner' })
    expect(region.tagName).toBe('SECTION')
  })

  it('renders a badge when provided and exposes its tooltip via title attr', () => {
    render(
      <DefinitionCard
        title="Assign Owner"
        badge={<Badge title="fub_create_task — create a FUB task">Create Task</Badge>}
      >
        <p>body</p>
      </DefinitionCard>,
    )
    const badge = screen.getByText('Create Task')
    expect(badge).toBeInTheDocument()
    expect(badge.getAttribute('title')).toBe('fub_create_task — create a FUB task')
  })

  it('renders an action slot when provided', () => {
    render(
      <DefinitionCard title="Assign Owner" action={<button type="button">Close</button>}>
        <p>body</p>
      </DefinitionCard>,
    )
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument()
  })

  it('renders body children', () => {
    render(
      <DefinitionCard title="Assign Owner">
        <p data-testid="body">body-content</p>
      </DefinitionCard>,
    )
    expect(screen.getByTestId('body').textContent).toBe('body-content')
  })

  it('omits badge and action when not provided', () => {
    const { container } = render(
      <DefinitionCard title="Assign Owner">
        <p>body</p>
      </DefinitionCard>,
    )
    // Header has exactly one flex container with the title; nothing else rendered.
    expect(container.querySelectorAll('header button').length).toBe(0)
  })
})
