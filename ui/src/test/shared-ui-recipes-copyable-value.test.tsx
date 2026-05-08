import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CopyableValue } from '../shared/ui/recipes/CopyableValue'

function mockClipboard() {
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(globalThis.navigator, 'clipboard', {
    value: { writeText },
    configurable: true,
  })
  return writeText
}

describe('CopyableValue recipe', () => {
  beforeEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('renders the raw value by default', () => {
    render(<CopyableValue value="https://example.com/long/path" />)
    expect(screen.getByText('https://example.com/long/path')).toBeInTheDocument()
  })

  it('renders a custom display node while keeping the raw value for copy', async () => {
    const writeText = mockClipboard()
    render(<CopyableValue value="raw-payload" display={<em>pretty</em>} />)
    expect(screen.getByText('pretty')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Copy' }))
    await Promise.resolve()
    expect(writeText).toHaveBeenCalledWith('raw-payload')
  })

  it('writes the value to the clipboard on click', async () => {
    const writeText = mockClipboard()
    render(<CopyableValue value="abc" />)
    fireEvent.click(screen.getByRole('button', { name: 'Copy' }))
    await Promise.resolve()
    expect(writeText).toHaveBeenCalledWith('abc')
  })

  it('swaps the label to Copied for ~1.5s after a successful copy', async () => {
    mockClipboard()
    vi.useFakeTimers()
    render(<CopyableValue value="abc" />)
    fireEvent.click(screen.getByRole('button', { name: 'Copy' }))
    await act(async () => {
      await Promise.resolve()
    })
    expect(screen.getByRole('button', { name: 'Copy' }).textContent).toBe('Copied')
    act(() => {
      vi.advanceTimersByTime(1500)
    })
    expect(screen.getByRole('button', { name: 'Copy' }).textContent).toBe('Copy')
  })

  it('disables the button when the value is empty', () => {
    render(<CopyableValue value="" />)
    const button = screen.getByRole('button', { name: 'Copy' })
    expect(button).toBeDisabled()
  })
})
