import { act, render, screen } from '@testing-library/react'
import { fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NotifyProvider } from '../shared/notifications/NotifyProvider'
import { useNotify } from '../shared/notifications/useNotify'

function NotifyHarness() {
  const notify = useNotify()

  return (
    <div>
      <button type="button" onClick={() => notify.success('Done')}>
        Success
      </button>
      <button type="button" onClick={() => notify.error('Failed')}>
        Error
      </button>
      <button type="button" onClick={() => notify.warning('Careful')}>
        Warning
      </button>
      <button type="button" onClick={() => notify.info('Heads up')}>
        Info
      </button>
    </div>
  )
}

describe('NotifyProvider', () => {
  it('shows variant toasts from nested hook usage', async () => {
    render(
      <NotifyProvider>
        <NotifyHarness />
      </NotifyProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Success' }))
    fireEvent.click(screen.getByRole('button', { name: 'Error' }))
    fireEvent.click(screen.getByRole('button', { name: 'Warning' }))
    fireEvent.click(screen.getByRole('button', { name: 'Info' }))

    expect(screen.getByText('Done')).toBeInTheDocument()
    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText('Careful')).toBeInTheDocument()
    expect(screen.getByText('Heads up')).toBeInTheDocument()
    expect(screen.getByTestId('notify-stack')).toHaveClass('fixed', 'right-4', 'top-4', 'z-[70]')
  })

  it('supports manual close and auto dismiss', async () => {
    vi.useFakeTimers()

    render(
      <NotifyProvider>
        <NotifyHarness />
      </NotifyProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Success' }))
    expect(screen.getByText('Done')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }))
    expect(screen.queryByText('Done')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Success' }))
    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(screen.queryByText('Done')).not.toBeInTheDocument()
    vi.useRealTimers()
  })
})
