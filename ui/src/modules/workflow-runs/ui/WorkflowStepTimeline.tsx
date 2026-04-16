import type { ReactNode } from 'react'
import { uiText } from '../../../shared/constants/uiText'
import { formatDateTime } from '../../../shared/lib/date'
import { JsonViewer } from '../../../shared/ui/JsonViewer'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { WorkflowRunStepDetail } from '../../workflows/lib/workflowSchemas'
import { formatWorkflowRunStepStatus, getWorkflowRunStepStatusTone } from '../lib/workflowRunsDisplay'

type WorkflowStepTimelineProps = {
  steps: WorkflowRunStepDetail[]
}

export function WorkflowStepTimeline({ steps }: WorkflowStepTimelineProps) {
  if (steps.length === 0) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.emptyMessage}</p>
  }

  return (
    <ol className="space-y-3">
      {steps.map((step) => (
        <li key={step.id} className="rounded-md border border-[var(--color-border)] p-3 text-sm">
          <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
            <StepField label={uiText.workflowRuns.stepNodeIdLabel} value={step.nodeId} />
            <StepField label={uiText.workflowRuns.stepTypeLabel} value={step.stepType} />
            <StepField
              label={uiText.workflowRuns.stepStatusLabel}
              value={<StatusBadge label={formatWorkflowRunStepStatus(step.status)} tone={getWorkflowRunStepStatusTone(step.status)} />}
            />
            <StepField label={uiText.workflowRuns.stepResultCodeLabel} value={step.resultCode ?? uiText.workflowRuns.missingValue} />
            <StepField label={uiText.workflowRuns.stepRetryCountLabel} value={step.retryCount ?? uiText.workflowRuns.missingValue} />
            <StepField label={uiText.workflowRuns.stepDueAtLabel} value={formatNullableDate(step.dueAt)} />
            <StepField label={uiText.workflowRuns.stepStartedAtLabel} value={formatNullableDate(step.startedAt)} />
            <StepField label={uiText.workflowRuns.stepCompletedAtLabel} value={formatNullableDate(step.completedAt)} />
          </div>
          {(step.outputs || step.errorMessage) && (
            <details className="mt-3 rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] p-2">
              <summary className="cursor-pointer text-xs font-medium uppercase tracking-wide text-[var(--color-text-muted)]">
                {uiText.workflowRuns.stepDetailsToggle}
              </summary>
              <div className="mt-2 space-y-3">
                {step.outputs && (
                  <div>
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-[var(--color-text-muted)]">
                      {uiText.workflowRuns.stepOutputsLabel}
                    </p>
                    <JsonViewer value={step.outputs} />
                  </div>
                )}
                {step.errorMessage && (
                  <p className="text-sm text-[var(--color-status-bad-text)]">
                    <span className="font-medium">{uiText.workflowRuns.stepErrorLabel}: </span>
                    {step.errorMessage}
                  </p>
                )}
              </div>
            </details>
          )}
        </li>
      ))}
    </ol>
  )
}

function StepField({ label, value }: { label: string; value: ReactNode }) {
  return (
    <p className="flex gap-2">
      <span className="text-[var(--color-text-muted)]">{label}:</span>
      <span>{value}</span>
    </p>
  )
}

function formatNullableDate(value: string | null): string {
  return value ? formatDateTime(value) : uiText.workflowRuns.missingValue
}
