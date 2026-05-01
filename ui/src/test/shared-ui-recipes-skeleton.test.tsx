import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Skeleton } from '../shared/ui/recipes/Skeleton'

describe('Skeleton recipe', () => {
  it('defaults to the line shape', () => {
    render(<Skeleton data-testid="sk" />)
    const el = screen.getByTestId('sk')
    expect(el.className).toMatch(/rounded-full/)
    expect(el.className).toMatch(/h-3/)
  })

  it('renders the block shape when requested', () => {
    render(<Skeleton shape="block" data-testid="sk" />)
    const el = screen.getByTestId('sk')
    expect(el.className).toMatch(/h-24/)
    expect(el.className).not.toMatch(/rounded-full/)
  })

  it('exposes loading state to assistive tech', () => {
    render(<Skeleton />)
    const el = screen.getByRole('status')
    expect(el.getAttribute('aria-busy')).toBe('true')
  })

  it('merges caller className', () => {
    render(<Skeleton className="custom-skeleton" data-testid="sk" />)
    expect(screen.getByTestId('sk').className).toMatch(/custom-skeleton/)
  })
})
