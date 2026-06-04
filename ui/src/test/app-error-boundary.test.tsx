import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AppErrorBoundary } from '../app/AppErrorBoundary'
import { uiText } from '../shared/constants/uiText'

function Boom(): never {
  throw new Error('boom')
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
    expect(screen.getByRole('button', { name: uiText.appError.reload })).toBeInTheDocument()

    spy.mockRestore()
  })
})
