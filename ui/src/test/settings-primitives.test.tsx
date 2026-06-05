import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { RefreshIcon, SettingsIcon, Toggle } from '../shared/ui'

describe('Toggle', () => {
  it('renders an accessible switch reflecting checked state and toggles', () => {
    const onCheckedChange = vi.fn()
    render(<Toggle checked={false} onCheckedChange={onCheckedChange} ariaLabel="Weekdays only" />)

    const sw = screen.getByRole('switch', { name: 'Weekdays only' })
    expect(sw).toHaveAttribute('aria-checked', 'false')

    fireEvent.click(sw)
    expect(onCheckedChange).toHaveBeenCalledWith(true)
  })

  it('does not fire when disabled', () => {
    const onCheckedChange = vi.fn()
    render(<Toggle checked onCheckedChange={onCheckedChange} ariaLabel="Worker" disabled />)

    fireEvent.click(screen.getByRole('switch'))
    expect(onCheckedChange).not.toHaveBeenCalled()
  })
})

describe('lucide-backed icons', () => {
  it('are decorative (aria-hidden) and default to the 16px icon size', () => {
    const { container } = render(<SettingsIcon />)
    const svg = container.querySelector('svg')
    expect(svg).toHaveAttribute('aria-hidden', 'true')
    expect(svg?.getAttribute('class')).toContain('h-4')
    expect(svg?.getAttribute('class')).toContain('w-4')
  })

  it('forward className and render an svg', () => {
    const { container } = render(
      <>
        <SettingsIcon className="h-[18px] w-[18px]" />
        <RefreshIcon className="h-4 w-4" />
      </>,
    )
    expect(container.querySelectorAll('svg')).toHaveLength(2)
  })
})
