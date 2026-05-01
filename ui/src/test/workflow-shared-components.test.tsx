import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { JsonViewer } from '../shared/ui/JsonViewer'
import { PagePagination } from '../shared/ui/PagePagination'

describe('workflow shared components', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders page pagination and handles prev/next button state', () => {
    const onPageChange = vi.fn()

    render(<PagePagination page={1} size={10} total={35} onPageChange={onPageChange} />)

    expect(screen.getByText('Page 2 of 4')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Previous' }))
    expect(onPageChange).toHaveBeenCalledWith(0)

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it('renders json and supports copy action', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })

    render(<JsonViewer value={{ key: 'value' }} />)

    expect(screen.getByText(/"key": "value"/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Copy' }))

    expect(writeText).toHaveBeenCalledWith('{\n  "key": "value"\n}')
  })
})
