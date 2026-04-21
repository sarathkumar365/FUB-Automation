/**
 * Full-width validation strip that sits above the storyboard.
 *
 * Five visual states: idle (subtle hint + Validate CTA), pending (spinner +
 * "Validation In Progress"), valid (green check), invalid (amber warning +
 * expandable issue list), error (red + retry). Collapsed state is a thin
 * 40px strip; expanding an invalid state reveals the full issue list inline.
 */
import { useState } from 'react'
import { uiText } from '../../../../../shared/constants/uiText'
import { Button } from '../../../../../shared/ui/button'
import type { ValidationViewState } from '../lib/useWorkflowDetailActions'

export interface ValidationStripProps {
  state: ValidationViewState
  onValidate: () => void
  onDismiss: () => void
  disabled?: boolean
}

export function ValidationStrip({ state, onValidate, onDismiss, disabled }: ValidationStripProps) {
  const [showIssues, setShowIssues] = useState(true)

  const tone = toneFor(state.mode)

  return (
    <div
      data-testid="workflow-validation-strip"
      data-validation-mode={state.mode}
      className="w-full rounded-lg border"
      style={{
        borderColor: tone.border,
        background: tone.background,
      }}
    >
      <div className="flex items-center gap-3 px-4 py-2.5">
        <span
          aria-hidden
          className="inline-flex h-2 w-2 rounded-full"
          style={{ background: tone.dot }}
        />
        <div className="flex-1 text-sm" style={{ color: tone.text }}>
          {state.mode === 'idle' ? (
            <span className="text-[var(--color-text-muted)]">
              {uiText.workflows.storyboardValidationIdleHint}
            </span>
          ) : null}
          {state.mode === 'pending' ? (
            <span className="font-medium">{uiText.workflows.validationPendingTitle}</span>
          ) : null}
          {state.mode === 'valid' ? (
            <span className="font-medium">{uiText.workflows.storyboardValidationValidLabel}</span>
          ) : null}
          {state.mode === 'invalid' ? (
            <span className="flex items-center gap-2">
              <span className="font-medium">
                {uiText.workflows.storyboardValidationInvalidLabel}
              </span>
              <span className="text-[var(--color-text-muted)]">·</span>
              <span>{uiText.workflows.storyboardValidationIssuesCount(state.errors.length)}</span>
            </span>
          ) : null}
          {state.mode === 'error' ? (
            <span className="font-medium">{state.message}</span>
          ) : null}
        </div>
        <div className="flex items-center gap-2">
          {state.mode === 'invalid' && state.errors.length > 0 ? (
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => setShowIssues((prev) => !prev)}
            >
              {showIssues
                ? uiText.workflows.storyboardValidationHideIssues
                : uiText.workflows.storyboardValidationShowIssues}
            </Button>
          ) : null}
          {state.mode === 'error' ? (
            <Button type="button" size="sm" variant="outline" onClick={onValidate} disabled={disabled}>
              {uiText.workflows.storyboardValidationRetry}
            </Button>
          ) : null}
          {state.mode !== 'idle' && state.mode !== 'pending' ? (
            <Button type="button" size="sm" variant="ghost" onClick={onDismiss}>
              {uiText.workflows.validationDismiss}
            </Button>
          ) : null}
        </div>
      </div>
      {state.mode === 'invalid' && showIssues && state.errors.length > 0 ? (
        <div className="border-t px-4 py-3" style={{ borderColor: tone.border }}>
          <ul className="list-inside list-disc text-sm text-[var(--color-text-muted)]">
            {state.errors.map((err) => (
              <li
                key={err}
                style={{ overflowWrap: 'anywhere', wordBreak: 'break-word' }}
              >
                {err}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  )
}

type ToneSet = { border: string; background: string; dot: string; text: string }

function toneFor(mode: ValidationViewState['mode']): ToneSet {
  switch (mode) {
    case 'valid':
      return {
        border: 'var(--color-status-ok)',
        background: 'var(--color-status-ok-bg)',
        dot: 'var(--color-status-ok)',
        text: 'var(--color-status-ok)',
      }
    case 'invalid':
      return {
        border: 'var(--color-status-warn)',
        background: 'var(--color-status-warn-bg)',
        dot: 'var(--color-status-warn)',
        text: 'var(--color-status-warn)',
      }
    case 'error':
      return {
        border: 'var(--color-status-bad)',
        background: 'var(--color-status-bad-bg)',
        dot: 'var(--color-status-bad)',
        text: 'var(--color-status-bad)',
      }
    case 'pending':
      return {
        border: 'var(--color-brand)',
        background: 'var(--color-brand-soft)',
        dot: 'var(--color-brand)',
        text: 'var(--color-brand)',
      }
    case 'idle':
    default:
      return {
        border: 'var(--color-border)',
        background: 'var(--color-surface)',
        dot: 'var(--color-border)',
        text: 'var(--color-text)',
      }
  }
}
