import { render, renderHook, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FullPageStatus } from '@app/status/FullPageStatus'
import { useRise } from '@shared/lib/useRise'
import { uiText } from '@shared/constants/uiText'
import { AlertTriangleIcon, ChevronDownIcon, ChevronLeftIcon, CompassIcon, LockIcon } from '@shared/ui'

describe('FullPageStatus', () => {
  it('renders the brand lockup and children by default', () => {
    render(
      <FullPageStatus>
        <p>body content</p>
      </FullPageStatus>,
    )
    expect(screen.getByText(uiText.authShell.wordmark)).toBeInTheDocument()
    expect(screen.getByText('body content')).toBeInTheDocument()
  })

  it('drops the lockup when inShell', () => {
    render(
      <FullPageStatus inShell>
        <p>shell body</p>
      </FullPageStatus>,
    )
    expect(screen.queryByText(uiText.authShell.wordmark)).not.toBeInTheDocument()
    expect(screen.getByText('shell body')).toBeInTheDocument()
  })

  it('drops the lockup when hideLockup', () => {
    render(
      <FullPageStatus hideLockup>
        <p>x</p>
      </FullPageStatus>,
    )
    expect(screen.queryByText(uiText.authShell.wordmark)).not.toBeInTheDocument()
  })
})

describe('useRise', () => {
  it('returns a ref without throwing', () => {
    const { result } = renderHook(() => useRise())
    expect(result.current).toHaveProperty('current')
  })
})

describe('status glyphs', () => {
  it('render as decorative svgs', () => {
    const { container } = render(
      <>
        <AlertTriangleIcon />
        <CompassIcon />
        <LockIcon />
        <ChevronLeftIcon />
        <ChevronDownIcon />
      </>,
    )
    const svgs = container.querySelectorAll('svg')
    expect(svgs).toHaveLength(5)
    expect(svgs[0]).toHaveAttribute('aria-hidden', 'true')
  })
})
