import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { WorkflowEditModal } from '../modules/workflows/ui/WorkflowEditModal'

const baseWorkflow = {
  id: 1,
  key: 'wf_a',
  name: 'Workflow A',
  description: null,
  trigger: {},
  graph: {},
  status: 'DRAFT' as const,
  versionNumber: 1,
  version: 1,
}

describe('workflow edit modal', () => {
  it('validates JSON/object inputs and blocks submit', async () => {
    const onSubmit = vi.fn(async () => {})
    const user = userEvent.setup()

    render(
      <WorkflowEditModal
        open
        onOpenChange={vi.fn()}
        workflow={baseWorkflow}
        onSubmit={onSubmit}
        isSubmitting={false}
      />,
    )

    await user.clear(screen.getByLabelText('Name'))
    await user.clear(screen.getByLabelText('Trigger JSON'))
    await user.type(screen.getByLabelText('Trigger JSON'), '[[')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))

    expect(await screen.findByText('Workflow name is required.')).toBeInTheDocument()
    expect(screen.getByText('Invalid JSON.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits parsed update payload for valid form', async () => {
    const onSubmit = vi.fn(async () => {})
    const user = userEvent.setup()

    render(
      <WorkflowEditModal
        open
        onOpenChange={vi.fn()}
        workflow={baseWorkflow}
        onSubmit={onSubmit}
        isSubmitting={false}
      />,
    )

    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Updated Workflow')
    await user.click(screen.getByRole('button', { name: 'Save Changes' }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Updated Workflow',
          trigger: {},
          graph: {},
        }),
      )
    })
  })
})
