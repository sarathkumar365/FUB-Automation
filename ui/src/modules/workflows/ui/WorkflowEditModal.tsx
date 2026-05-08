import * as Dialog from '@radix-ui/react-dialog'
import { useMemo, useState } from 'react'
import type { UpdateWorkflowCommand } from '../../../platform/ports/workflowPort'
import type { WorkflowResponse } from '../lib/workflowSchemas'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import { Input } from '../../../shared/ui/input'

type WorkflowEditModalProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  workflow: WorkflowResponse
  onSubmit: (command: UpdateWorkflowCommand) => Promise<void>
  isSubmitting: boolean
}

type EditFormState = {
  name: string
  description: string
  triggerJson: string
  graphJson: string
}

type EditFormErrors = {
  name?: string
  triggerJson?: string
  graphJson?: string
}

export function WorkflowEditModal({
  open,
  onOpenChange,
  workflow,
  onSubmit,
  isSubmitting,
}: WorkflowEditModalProps) {
  const [formState, setFormState] = useState<EditFormState>(() => toFormState(workflow))
  const [errors, setErrors] = useState<EditFormErrors>({})
  const canSubmit = useMemo(() => !isSubmitting, [isSubmitting])

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
    if (!nextOpen) {
      setErrors({})
    }
  }

  const handleSubmit = async () => {
    const validation = validateForm(formState)
    setErrors(validation.errors)
    if (!validation.valid || !validation.command) {
      return
    }
    await onSubmit(validation.command)
  }

  return (
    <Dialog.Root open={open} onOpenChange={handleOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,760px)] -translate-x-1/2 -translate-y-1/2 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-lg focus:outline-none">
          <Dialog.Title className="text-lg font-semibold text-[var(--color-text)]">{uiText.workflows.edit.title}</Dialog.Title>
          <Dialog.Description className="mt-2 text-sm text-[var(--color-text-muted)]">
            {uiText.workflows.edit.description}
          </Dialog.Description>

          <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
            <Field label={uiText.workflows.edit.nameLabel} error={errors.name}>
              <Input
                value={formState.name}
                onChange={(event) => setFormState((existing) => ({ ...existing, name: event.target.value }))}
                disabled={isSubmitting}
                aria-label={uiText.workflows.edit.nameLabel}
              />
            </Field>
            <Field label={uiText.workflows.edit.descriptionLabel}>
              <Input
                value={formState.description}
                onChange={(event) => setFormState((existing) => ({ ...existing, description: event.target.value }))}
                disabled={isSubmitting}
                aria-label={uiText.workflows.edit.descriptionLabel}
              />
            </Field>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4">
            <JsonField
              label={uiText.workflows.edit.triggerJsonLabel}
              value={formState.triggerJson}
              onChange={(value) => setFormState((existing) => ({ ...existing, triggerJson: value }))}
              error={errors.triggerJson}
              disabled={isSubmitting}
            />
            <JsonField
              label={uiText.workflows.edit.graphJsonLabel}
              value={formState.graphJson}
              onChange={(value) => setFormState((existing) => ({ ...existing, graphJson: value }))}
              error={errors.graphJson}
              disabled={isSubmitting}
            />
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              {uiText.dialog.cancel}
            </Button>
            <Button onClick={handleSubmit} disabled={!canSubmit}>
              {isSubmitting ? uiText.workflows.edit.submitting : uiText.workflows.edit.submit}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm font-medium text-[var(--color-text)]">{label}</span>
      {children}
      {error ? <span className="text-xs text-[var(--color-status-bad-text)]">{error}</span> : null}
    </label>
  )
}

function JsonField({
  label,
  value,
  onChange,
  error,
  disabled,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  disabled: boolean
}) {
  return (
    <Field label={label} error={error}>
      <textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
        aria-label={label}
        className="h-28 w-full rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 font-mono text-xs text-[var(--color-text)] outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)]"
      />
    </Field>
  )
}

function toFormState(workflow: WorkflowResponse): EditFormState {
  return {
    name: workflow.name ?? '',
    description: workflow.description ?? '',
    triggerJson: JSON.stringify(workflow.trigger ?? {}, null, 2),
    graphJson: JSON.stringify(workflow.graph ?? {}, null, 2),
  }
}

function validateForm(formState: EditFormState): {
  valid: boolean
  command?: UpdateWorkflowCommand
  errors: EditFormErrors
} {
  const errors: EditFormErrors = {}
  const name = formState.name.trim()
  const description = formState.description.trim()

  if (!name) {
    errors.name = uiText.workflows.edit.nameRequired
  }

  let trigger: Record<string, unknown> | undefined
  let graph: Record<string, unknown> | undefined

  try {
    const parsed = JSON.parse(formState.triggerJson || '{}')
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
      errors.triggerJson = uiText.workflows.edit.jsonObjectRequired
    } else {
      trigger = parsed as Record<string, unknown>
    }
  } catch {
    errors.triggerJson = uiText.workflows.edit.invalidJson
  }

  try {
    const parsed = JSON.parse(formState.graphJson || '{}')
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
      errors.graphJson = uiText.workflows.edit.jsonObjectRequired
    } else {
      graph = parsed as Record<string, unknown>
    }
  } catch {
    errors.graphJson = uiText.workflows.edit.invalidJson
  }

  if (Object.keys(errors).length > 0 || !trigger || !graph) {
    return { valid: false, errors }
  }

  return {
    valid: true,
    errors,
    command: {
      name,
      description: description || undefined,
      trigger,
      graph,
    },
  }
}
