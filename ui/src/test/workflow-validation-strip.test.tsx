import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ValidationStrip } from '../modules/workflows/ui/WorkflowDetailPage/StoryboardTab/ValidationStrip'

describe('ValidationStrip', () => {
  it('shows the idle hint by default', () => {
    render(<ValidationStrip state={{ mode: 'idle' }} onValidate={vi.fn()} onDismiss={vi.fn()} />)
    expect(screen.getByText(/Click Validate/)).toBeInTheDocument()
  })

  it('shows the pending label while validating', () => {
    render(<ValidationStrip state={{ mode: 'pending' }} onValidate={vi.fn()} onDismiss={vi.fn()} />)
    expect(screen.getByText('Validation In Progress')).toBeInTheDocument()
  })

  it('shows a valid label and dismiss button on success', () => {
    render(<ValidationStrip state={{ mode: 'valid' }} onValidate={vi.fn()} onDismiss={vi.fn()} />)
    expect(screen.getByText('Definition is valid')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dismiss Validation' })).toBeInTheDocument()
  })

  it('shows invalid label with issue count and can expand issues', () => {
    render(
      <ValidationStrip
        state={{ mode: 'invalid', errors: ['missing node a', 'unreachable node b'] }}
        onValidate={vi.fn()}
        onDismiss={vi.fn()}
      />,
    )
    expect(screen.getByText('Definition has validation issues')).toBeInTheDocument()
    expect(screen.getByText('2 issues')).toBeInTheDocument()
  })

  it('shows the error message and retry button on request failure', () => {
    render(
      <ValidationStrip
        state={{ mode: 'error', message: 'Validation request failed' }}
        onValidate={vi.fn()}
        onDismiss={vi.fn()}
      />,
    )
    expect(screen.getByText('Validation request failed')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })
})
