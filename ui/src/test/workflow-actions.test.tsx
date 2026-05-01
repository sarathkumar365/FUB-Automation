import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { WorkflowActions } from '../modules/workflows/ui/WorkflowActions'

describe('workflow actions', () => {
  it('applies status gating to buttons', () => {
    render(
      <WorkflowActions
        canEdit={false}
        canValidate={true}
        canActivate={false}
        canDeactivate={true}
        canArchive={false}
        isPending={false}
        onEdit={vi.fn()}
        onValidate={async () => {}}
        onActivate={async () => {}}
        onDeactivate={async () => {}}
        onArchive={async () => {}}
      />,
    )

    expect(screen.getByRole('button', { name: 'Edit' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Validate' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Activate' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Deactivate' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Archive' })).toBeDisabled()
  })

  it('runs activation callback after confirmation', async () => {
    const user = userEvent.setup()
    const onActivate = vi.fn(async () => {})

    render(
      <WorkflowActions
        canEdit={true}
        canValidate={true}
        canActivate={true}
        canDeactivate={false}
        canArchive={false}
        isPending={false}
        onEdit={vi.fn()}
        onValidate={async () => {}}
        onActivate={onActivate}
        onDeactivate={async () => {}}
        onArchive={async () => {}}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Activate' }))
    expect(screen.getByRole('heading', { name: 'Confirm Activate' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm' }))

    await waitFor(() => {
      expect(onActivate).toHaveBeenCalled()
    })
  })
})

