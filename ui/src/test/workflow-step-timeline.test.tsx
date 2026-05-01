import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { WorkflowStepTimeline } from '../modules/workflow-runs/ui/WorkflowStepTimeline'

describe('workflow step timeline', () => {
  it('renders step metadata and expandable details', () => {
    render(
      <WorkflowStepTimeline
        steps={[
          {
            id: 1,
            nodeId: 'n1',
            stepType: 'fub_add_tag',
            status: 'FAILED',
            resultCode: 'HTTP_500',
            outputs: { attempts: 2 },
            errorMessage: 'request failed',
            retryCount: 2,
            dueAt: null,
            startedAt: '2026-04-16T10:00:00Z',
            completedAt: '2026-04-16T10:01:00Z',
          },
        ]}
      />,
    )

    expect(screen.getByText('n1')).toBeInTheDocument()
    expect(screen.getByText('fub_add_tag')).toBeInTheDocument()
    expect(screen.getByText('HTTP_500')).toBeInTheDocument()
    expect(screen.getByText('Step details')).toBeInTheDocument()
    expect(screen.getByText('Outputs')).toBeInTheDocument()
    expect(screen.getByText('Error:')).toBeInTheDocument()
    expect(screen.getByText('request failed')).toBeInTheDocument()
  })
})
