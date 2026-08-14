import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SegmentedControl } from '@shared/ui/SegmentedControl'

const options = [
  { value: 'yesterday', label: 'Yesterday' },
  { value: 'today', label: 'Today' },
] as const

describe('segmented control', () => {
  it('renders a labelled group of pressable buttons', () => {
    render(
      <SegmentedControl options={options} value="yesterday" onChange={() => {}} ariaLabel="Time window" />,
    )

    expect(screen.getByRole('group', { name: 'Time window' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Yesterday' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Today' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('reports the clicked value', () => {
    const onChange = vi.fn()
    render(
      <SegmentedControl options={options} value="yesterday" onChange={onChange} ariaLabel="Time window" />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Today' }))

    expect(onChange).toHaveBeenCalledWith('today')
  })
})
