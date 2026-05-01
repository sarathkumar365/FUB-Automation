import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { WorkflowCreateModal } from '../modules/workflows/ui/WorkflowCreateModal'

describe('workflow create modal', () => {
  it('shows JSON validation errors and blocks submit', async () => {
    const onSubmit = vi.fn(async () => {})
    const user = userEvent.setup()

    render(
      <WorkflowCreateModal
        open
        onOpenChange={vi.fn()}
        onSubmit={onSubmit}
        isSubmitting={false}
      />,
    )

    await user.type(screen.getByLabelText('Key'), 'wf_invalid_json')
    await user.type(screen.getByLabelText('Name'), 'Invalid JSON Workflow')
    await user.clear(screen.getByLabelText('Trigger JSON'))
    await user.type(screen.getByLabelText('Trigger JSON'), '{{')
    await user.click(screen.getByRole('button', { name: 'Create' }))

    expect(await screen.findByText('Invalid JSON.')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits parsed payload when form is valid', async () => {
    const onSubmit = vi.fn(async () => {})
    const user = userEvent.setup()

    render(
      <WorkflowCreateModal
        open
        onOpenChange={vi.fn()}
        onSubmit={onSubmit}
        isSubmitting={false}
      />,
    )

    await user.type(screen.getByLabelText('Key'), 'wf_create')
    await user.type(screen.getByLabelText('Name'), 'Create Workflow')
    await user.selectOptions(screen.getByLabelText('Status'), 'ACTIVE')
    await user.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          key: 'wf_create',
          name: 'Create Workflow',
          status: 'ACTIVE',
          trigger: {},
          graph: {},
        }),
      )
    })
  })

  it('shows submitting state on action button', () => {
    render(
      <WorkflowCreateModal
        open
        onOpenChange={vi.fn()}
        onSubmit={vi.fn(async () => {})}
        isSubmitting
      />,
    )

    expect(screen.getByRole('button', { name: 'Creating...' })).toBeDisabled()
  })
})
