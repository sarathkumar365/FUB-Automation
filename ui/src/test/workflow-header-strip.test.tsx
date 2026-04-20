import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { WorkflowHeaderStrip } from '../modules/workflows/ui/WorkflowDetailPage/WorkflowHeaderStrip'
import type { WorkflowResponse } from '../modules/workflows/lib/workflowSchemas'

const workflow: WorkflowResponse = {
  id: 1,
  key: 'lead_intake_v1',
  name: 'Lead Intake',
  description: 'Intake workflow',
  trigger: { type: 'webhook' },
  graph: {},
  status: 'DRAFT',
  versionNumber: 3,
  version: 3,
}

describe('WorkflowHeaderStrip', () => {
  it('renders the workflow name, status chip, version chip, and trigger chip', () => {
    render(
      <MemoryRouter>
        <WorkflowHeaderStrip
          workflow={workflow}
          isAnyActionPending={false}
          onEdit={vi.fn()}
          onValidate={vi.fn(async () => {})}
          onActivate={vi.fn(async () => {})}
          onDeactivate={vi.fn(async () => {})}
          onArchive={vi.fn(async () => {})}
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { level: 1, name: 'Lead Intake' })).toBeInTheDocument()
    // Status badge renders the formatted status label ("Draft").
    expect(screen.getByText('Draft')).toBeInTheDocument()
    // Version chip prints v3.
    expect(screen.getByText('v3')).toBeInTheDocument()
    // Trigger chip exposes a data-testid and prints the trigger type.
    const triggerChip = screen.getByTestId('workflow-header-trigger-chip')
    expect(triggerChip).toHaveTextContent('webhook')
    // Edit button is rendered (DRAFT is editable).
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })
})
