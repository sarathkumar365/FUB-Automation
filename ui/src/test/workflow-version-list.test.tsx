import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { WorkflowVersionList } from '../modules/workflows/ui/WorkflowVersionList'

const versions = [
  {
    versionNumber: 2,
    status: 'ACTIVE' as const,
    createdAt: '2026-04-16T10:00:00Z',
    updatedAt: '2026-04-16T10:00:00Z',
  },
  {
    versionNumber: 1,
    status: 'DRAFT' as const,
    createdAt: '2026-04-15T10:00:00Z',
    updatedAt: '2026-04-15T10:00:00Z',
  },
]

describe('workflow version list', () => {
  it('disables rollback for current version and triggers callback for prior version', async () => {
    const user = userEvent.setup()
    const onRequestRollback = vi.fn()

    render(
      <WorkflowVersionList
        versions={versions}
        currentVersionNumber={2}
        onRequestRollback={onRequestRollback}
        isRollbackPending={false}
      />,
    )

    const rollbackButtons = screen.getAllByRole('button', { name: 'Rollback' })
    expect(rollbackButtons[0]).toBeDisabled()
    expect(rollbackButtons[1]).toBeEnabled()

    await user.click(rollbackButtons[1])
    expect(onRequestRollback).toHaveBeenCalledWith(1)
  })
})

