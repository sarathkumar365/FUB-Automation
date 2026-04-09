import type { PolicyExecutionStep } from '../lib/policySchemas'
import { stepStatusLabel, stepStatusTone, stepTypeLabel } from '../lib/policiesDisplay'
import { formatDateTime } from '../../../shared/lib/date'

type StepTimelineProps = {
  steps: PolicyExecutionStep[]
}

export function StepTimeline({ steps }: StepTimelineProps) {
  if (steps.length === 0) {
    return <p className="text-sm text-[var(--color-text-muted)]">No steps recorded.</p>
  }

  const sorted = [...steps].sort((a, b) => a.stepOrder - b.stepOrder)

  return (
    <div className="space-y-0">
      {sorted.map((step, index) => (
        <StepNode key={step.id} step={step} isLast={index === sorted.length - 1} />
      ))}
    </div>
  )
}

function StepNode({ step, isLast }: { step: PolicyExecutionStep; isLast: boolean }) {
  const tone = stepStatusTone(step.status)
  const iconColor = toneToColor(tone)
  const isTerminal = step.status === 'COMPLETED' || step.status === 'FAILED' || step.status === 'SKIPPED'

  return (
    <div className="flex gap-3">
      {/* Vertical line + icon */}
      <div className="flex flex-col items-center">
        <div
          className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 ${iconColor.border} ${iconColor.bg}`}
        >
          <StepIcon status={step.status} className={`h-3 w-3 ${iconColor.text}`} />
        </div>
        {!isLast && <div className="w-px flex-1 bg-[var(--color-border)]" />}
      </div>

      {/* Content */}
      <div className={`pb-4 ${isLast ? '' : ''}`}>
        <p className="text-sm font-medium text-[var(--color-text)]">
          {step.stepOrder + 1}. {stepTypeLabel(step.stepType)}
        </p>

        <p className={`text-xs font-medium ${iconColor.text}`}>
          {step.resultCode ?? stepStatusLabel(step.status)}
        </p>

        {step.dueAt && (
          <p className="mt-0.5 font-mono text-xs text-[var(--color-text-muted)]">
            Due: {formatDateTime(step.dueAt)}
          </p>
        )}

        {isTerminal && (
          <p className="font-mono text-xs text-[var(--color-text-muted)]">
            {step.status === 'SKIPPED' ? 'Skipped' : formatDateTime(step.updatedAt)}
          </p>
        )}

        {step.errorMessage && (
          <p className="mt-1 text-xs text-[var(--color-status-bad)]">{step.errorMessage}</p>
        )}
      </div>
    </div>
  )
}

function StepIcon({ status, className }: { status: PolicyExecutionStep['status']; className: string }) {
  const svgProps = {
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2.5,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    className,
    'aria-hidden': true as const,
  }

  switch (status) {
    case 'COMPLETED':
      return (
        <svg {...svgProps}>
          <path d="M20 6 9 17l-5-5" />
        </svg>
      )
    case 'FAILED':
      return (
        <svg {...svgProps}>
          <path d="M18 6 6 18" />
          <path d="m6 6 12 12" />
        </svg>
      )
    case 'SKIPPED':
      return (
        <svg {...svgProps}>
          <path d="m5 4 10 8-10 8z" />
          <path d="M19 4v16" />
        </svg>
      )
    case 'PROCESSING':
      return (
        <svg {...svgProps}>
          <path d="M12 2v4" />
          <path d="M12 18v4" />
          <path d="M4.93 4.93l2.83 2.83" />
          <path d="M16.24 16.24l2.83 2.83" />
          <path d="M2 12h4" />
          <path d="M18 12h4" />
        </svg>
      )
    case 'PENDING':
    case 'WAITING_DEPENDENCY':
    default:
      return (
        <svg {...svgProps}>
          <circle cx="12" cy="12" r="10" />
          <path d="M12 6v6l4 2" />
        </svg>
      )
  }
}

function toneToColor(tone: 'success' | 'warning' | 'error' | 'info') {
  switch (tone) {
    case 'success':
      return {
        border: 'border-[var(--color-status-ok)]',
        bg: 'bg-[var(--color-status-ok-bg)]',
        text: 'text-[var(--color-status-ok)]',
      }
    case 'error':
      return {
        border: 'border-[var(--color-status-bad)]',
        bg: 'bg-[var(--color-status-bad-bg)]',
        text: 'text-[var(--color-status-bad)]',
      }
    case 'warning':
      return {
        border: 'border-[var(--color-status-warn)]',
        bg: 'bg-[var(--color-status-warn-bg)]',
        text: 'text-[var(--color-status-warn)]',
      }
    case 'info':
    default:
      return {
        border: 'border-[var(--color-border)]',
        bg: 'bg-[var(--color-surface-alt)]',
        text: 'text-[var(--color-text-muted)]',
      }
  }
}
