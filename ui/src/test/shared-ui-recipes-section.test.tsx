import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Section } from '../shared/ui/recipes/Section'

describe('Section recipe', () => {
  it('renders the caption with uppercase tracking', () => {
    render(
      <Section title="Config">
        <p>body</p>
      </Section>,
    )
    const caption = screen.getByText('Config')
    expect(caption.className).toMatch(/uppercase/)
    expect(caption.className).toMatch(/tracking-/)
  })

  it('renders children in the body', () => {
    render(
      <Section title="Config">
        <p>body-content</p>
      </Section>,
    )
    expect(screen.getByText('body-content')).toBeInTheDocument()
  })

  it('labels the section element with the caption for assistive tech', () => {
    render(
      <Section title="Transitions">
        <p>body</p>
      </Section>,
    )
    const region = screen.getByRole('region', { name: 'Transitions' })
    expect(region.tagName).toBe('SECTION')
  })

  it('renders the action slot when provided', () => {
    render(
      <Section title="Config" action={<button type="button">Edit</button>}>
        <p>body</p>
      </Section>,
    )
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })

  it('omits the action slot when not provided', () => {
    const { container } = render(
      <Section title="Config">
        <p>body</p>
      </Section>,
    )
    expect(container.querySelectorAll('button').length).toBe(0)
  })
})
