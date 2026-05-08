import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FIELD_ROW_LONG_THRESHOLD, FieldRow } from '../shared/ui/recipes/FieldRow'

function isInlineGrid(el: HTMLElement): boolean {
  return el.className.includes('grid-cols-')
}

function isStackedFlex(el: HTMLElement): boolean {
  return el.className.includes('flex-col')
}

describe('FieldRow recipe', () => {
  it('renders short string values inline under auto layout', () => {
    const { container } = render(<FieldRow label="Type" value="webhook" />)
    expect(isInlineGrid(container.firstChild as HTMLElement)).toBe(true)
  })

  it('renders long string values stacked under auto layout', () => {
    const long = 'a'.repeat(FIELD_ROW_LONG_THRESHOLD + 1)
    const { container } = render(<FieldRow label="URL" value={long} />)
    expect(isStackedFlex(container.firstChild as HTMLElement)).toBe(true)
  })

  it('renders multi-line strings stacked regardless of length', () => {
    const { container } = render(<FieldRow label="Notes" value={'line1\nline2'} />)
    expect(isStackedFlex(container.firstChild as HTMLElement)).toBe(true)
  })

  it('renders booleans inline with human label', () => {
    const { container } = render(<FieldRow label="Entry" value />)
    expect(isInlineGrid(container.firstChild as HTMLElement)).toBe(true)
    expect(screen.getByText('Yes')).toBeInTheDocument()
  })

  it('renders em-dash for null values', () => {
    render(<FieldRow label="Type" value={null} />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('renders ReactNode values stacked under auto layout', () => {
    const { container } = render(
      <FieldRow label="Status" value={<span data-testid="chip">active</span>} />,
    )
    expect(isStackedFlex(container.firstChild as HTMLElement)).toBe(true)
    expect(screen.getByTestId('chip')).toBeInTheDocument()
  })

  it('honors explicit inline layout for long strings', () => {
    const long = 'a'.repeat(FIELD_ROW_LONG_THRESHOLD + 1)
    const { container } = render(<FieldRow label="URL" value={long} layout="inline" />)
    expect(isInlineGrid(container.firstChild as HTMLElement)).toBe(true)
  })

  it('honors explicit stacked layout for short strings', () => {
    const { container } = render(<FieldRow label="Type" value="webhook" layout="stacked" />)
    expect(isStackedFlex(container.firstChild as HTMLElement)).toBe(true)
  })
})
