import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { KeyValueList } from '../shared/ui/recipes/KeyValueList'

describe('KeyValueList recipe', () => {
  it('renders a dl in grid variant (default)', () => {
    const { container } = render(
      <KeyValueList items={[{ label: 'Type', value: 'webhook' }, { label: 'Enabled', value: true }]} />,
    )
    const list = container.querySelector('dl')
    expect(list).not.toBeNull()
    expect(list?.querySelectorAll('dt')).toHaveLength(2)
    expect(list?.querySelectorAll('dd')).toHaveLength(2)
  })

  it('renders boolean values as Yes/No', () => {
    render(<KeyValueList items={[{ label: 'Enabled', value: true }, { label: 'Paused', value: false }]} />)
    expect(screen.getByText('Yes')).toBeInTheDocument()
    expect(screen.getByText('No')).toBeInTheDocument()
  })

  it('renders em-dash for null values', () => {
    render(<KeyValueList items={[{ label: 'Last run', value: null }]} />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('renders stacked FieldRows in stacked variant', () => {
    const { container } = render(
      <KeyValueList
        variant="stacked"
        items={[{ label: 'Type', value: 'webhook' }, { label: 'Path', value: '/leads' }]}
      />,
    )
    // In stacked mode, no <dl> — FieldRow renders its own flex-col wrapper.
    expect(container.querySelector('dl')).toBeNull()
    // Each row is a flex-col container.
    expect(container.querySelectorAll('.flex-col').length).toBeGreaterThanOrEqual(2)
  })

  it('accepts the stacked boolean shorthand', () => {
    const { container } = render(
      <KeyValueList stacked items={[{ label: 'Type', value: 'webhook' }]} />,
    )
    expect(container.querySelector('dl')).toBeNull()
  })

  it('renders the empty state when items is empty', () => {
    render(<KeyValueList items={[]} emptyState={<span>no values</span>} />)
    expect(screen.getByText('no values')).toBeInTheDocument()
  })

  it('renders nothing when empty with no emptyState', () => {
    const { container } = render(<KeyValueList items={[]} />)
    expect(container.firstChild).toBeNull()
  })
})
