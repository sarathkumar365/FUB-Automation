import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AppErrorBoundary } from '@app/AppErrorBoundary'
import { routes } from '@shared/constants/routes'
import { uiText } from '@shared/constants/uiText'

function Boom(): never {
  throw new Error('boom-detail')
}

describe('AppErrorBoundary', () => {
  it('renders the branded fallback when a child throws — with NO router context', () => {
    // React logs the caught error to console.error; silence it for a clean run.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

    // Deliberately NO MemoryRouter: the real AppErrorBoundary sits OUTSIDE
    // RouterProvider, so the fallback must render without a router (regression
    // test for the fallback using a plain <a>, not a react-router <Link>).
    render(
      <AppErrorBoundary>
        <Boom />
      </AppErrorBoundary>,
    )

    expect(screen.getByRole('heading', { name: uiText.appError.title })).toBeInTheDocument()
    expect(screen.getByText(uiText.appError.message)).toBeInTheDocument()
    expect(screen.getByText(uiText.appError.eyebrow)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: uiText.appError.reload })).toBeInTheDocument()
    // Plain anchor, not a router Link.
    expect(screen.getByRole('link', { name: uiText.appError.backToDashboard })).toHaveAttribute(
      'href',
      routes.dashboard,
    )

    spy.mockRestore()
  })

  it('reveals the dev-only error stack (with the thrown error) on expand', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <AppErrorBoundary>
        <Boom />
      </AppErrorBoundary>,
    )

    fireEvent.click(screen.getByRole('button', { name: new RegExp(uiText.appError.details.label, 'i') }))
    expect(screen.getByText(/boom-detail/)).toBeInTheDocument()

    spy.mockRestore()
  })

  it('shows the fallback even when a child throws a non-Error value (null)', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

    function ThrowNull(): never {
      throw null
    }
    render(
      <AppErrorBoundary>
        <ThrowNull />
      </AppErrorBoundary>,
    )

    expect(screen.getByRole('heading', { name: uiText.appError.title })).toBeInTheDocument()

    spy.mockRestore()
  })
})
