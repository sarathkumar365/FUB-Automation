import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { StatusScreen, type StatusContent } from '../app/status/StatusScreen'
import { AlertTriangleIcon } from '../shared/ui'

function content(overrides?: Partial<StatusContent>): StatusContent {
  return {
    tone: 'bad',
    glyph: AlertTriangleIcon,
    eyebrow: 'ERROR',
    title: 'Something went wrong',
    body: 'The console hit an unexpected error.',
    meta: 'BOUNDARY · 500',
    ...overrides,
  }
}

describe('StatusScreen', () => {
  it('renders eyebrow, title, body, and the console strip', () => {
    render(<StatusScreen {...content()} />)
    expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
    expect(screen.getByText('The console hit an unexpected error.')).toBeInTheDocument()
    expect(screen.getByText('ERROR')).toBeInTheDocument()
    expect(screen.getByText('BOUNDARY · 500')).toBeInTheDocument()
  })

  it('renders a primary button that fires onClick', () => {
    const onClick = vi.fn()
    render(<StatusScreen {...content({ primary: { label: 'Reload', onClick } })} />)
    fireEvent.click(screen.getByRole('button', { name: 'Reload' }))
    expect(onClick).toHaveBeenCalled()
  })

  it('renders the secondary as a link when href is given', () => {
    render(<StatusScreen {...content({ secondary: { label: 'Go to dashboard', href: '/admin-ui' } })} />)
    expect(screen.getByRole('link', { name: 'Go to dashboard' })).toHaveAttribute('href', '/admin-ui')
  })

  it('renders the secondary as a button when only onClick is given', () => {
    const onClick = vi.fn()
    render(<StatusScreen {...content({ secondary: { label: 'Back', onClick } })} />)
    fireEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(onClick).toHaveBeenCalled()
  })

  it('uses the watermark override when provided', () => {
    render(<StatusScreen {...content({ watermark: <div>404</div> })} />)
    expect(screen.getByText('404')).toBeInTheDocument()
  })
})
