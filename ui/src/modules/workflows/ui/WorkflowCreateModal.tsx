import * as Dialog from '@radix-ui/react-dialog'
import { useMemo, useState } from 'react'
import type { CreateWorkflowCommand } from '../../../platform/ports/workflowPort'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import { Input } from '../../../shared/ui/input'
import { Select } from '../../../shared/ui/select'
import type { WorkflowStatus } from '../lib/workflowSchemas'
import { formatWorkflowStatus } from '../lib/workflowsDisplay'

type WorkflowCreateModalProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (command: CreateWorkflowCommand) => Promise<void>
  isSubmitting: boolean
}

type CreateFormState = {
  key: string
  name: string
  description: string
  status: WorkflowStatus
  triggerJson: string
  graphJson: string
}

type CreateFormErrors = {
  key?: string
  name?: string
  triggerJson?: string
  graphJson?: string
}

const STATUSES: WorkflowStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED']

const DEFAULT_FORM: CreateFormState = {
  key: '',
  name: '',
  description: '',
  status: 'DRAFT',
  triggerJson: '{}',
  graphJson: '{}',
}

export function WorkflowCreateModal({ open, onOpenChange, onSubmit, isSubmitting }: WorkflowCreateModalProps) {
  const [formState, setFormState] = useState<CreateFormState>(DEFAULT_FORM)
  const [errors, setErrors] = useState<CreateFormErrors>({})

  const canSubmit = useMemo(() => !isSubmitting, [isSubmitting])

  const handleClose = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
    if (!nextOpen) {
      setErrors({})
      setFormState(DEFAULT_FORM)
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
    <Dialog.Root open={open} onOpenChange={handleClose}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,760px)] -translate-x-1/2 -translate-y-1/2 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-lg focus:outline-none">
          <Dialog.Title className="text-lg font-semibold text-[var(--color-text)]">{uiText.workflows.create.title}</Dialog.Title>
          <Dialog.Description className="mt-2 text-sm text-[var(--color-text-muted)]">{uiText.workflows.create.description}</Dialog.Description>

          <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
            <Field label={uiText.workflows.create.keyLabel} error={errors.key}>
              <Input
                value={formState.key}
                onChange={(event) => setFormState((existing) => ({ ...existing, key: event.target.value }))}
                disabled={isSubmitting}
                placeholder={uiText.workflows.create.keyPlaceholder}
                aria-label={uiText.workflows.create.keyLabel}
              />
            </Field>

            <Field label={uiText.workflows.create.nameLabel} error={errors.name}>
              <Input
                value={formState.name}
                onChange={(event) => setFormState((existing) => ({ ...existing, name: event.target.value }))}
                disabled={isSubmitting}
                placeholder={uiText.workflows.create.namePlaceholder}
                aria-label={uiText.workflows.create.nameLabel}
              />
            </Field>

            <Field label={uiText.workflows.create.descriptionLabel}>
              <Input
                value={formState.description}
                onChange={(event) => setFormState((existing) => ({ ...existing, description: event.target.value }))}
                disabled={isSubmitting}
                placeholder={uiText.workflows.create.descriptionPlaceholder}
                aria-label={uiText.workflows.create.descriptionLabel}
              />
            </Field>

            <Field label={uiText.workflows.create.statusLabel}>
              <Select
                value={formState.status}
                onChange={(event) =>
                  setFormState((existing) => ({
                    ...existing,
                    status: event.target.value as WorkflowStatus,
                  }))
                }
                aria-label={uiText.workflows.create.statusLabel}
                disabled={isSubmitting}
              >
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {formatWorkflowStatus(status)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4">
            <JsonField
              label={uiText.workflows.create.triggerJsonLabel}
              value={formState.triggerJson}
              onChange={(value) => setFormState((existing) => ({ ...existing, triggerJson: value }))}
              error={errors.triggerJson}
              disabled={isSubmitting}
            />
            <JsonField
              label={uiText.workflows.create.graphJsonLabel}
              value={formState.graphJson}
              onChange={(value) => setFormState((existing) => ({ ...existing, graphJson: value }))}
              error={errors.graphJson}
              disabled={isSubmitting}
            />
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button variant="outline" onClick={() => handleClose(false)} disabled={isSubmitting}>
              {uiText.dialog.cancel}
            </Button>
            <Button onClick={handleSubmit} disabled={!canSubmit}>
              {isSubmitting ? uiText.workflows.create.submitting : uiText.workflows.create.submit}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
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

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm font-medium text-[var(--color-text)]">{label}</span>
      {children}
      {error ? <span className="text-xs text-[var(--color-status-bad-text)]">{error}</span> : null}
    </label>
  )
}

function validateForm(formState: CreateFormState): {
  valid: boolean
  command?: CreateWorkflowCommand
  errors: CreateFormErrors
} {
  const errors: CreateFormErrors = {}

  const key = formState.key.trim()
  const name = formState.name.trim()
  const description = formState.description.trim()

  if (!key) {
    errors.key = uiText.workflows.create.keyRequired
  }
  if (!name) {
    errors.name = uiText.workflows.create.nameRequired
  }

  let trigger: Record<string, unknown> | undefined
  let graph: Record<string, unknown> | undefined

  try {
    const parsed = JSON.parse(formState.triggerJson || '{}')
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
      errors.triggerJson = uiText.workflows.create.jsonObjectRequired
    } else {
      trigger = parsed as Record<string, unknown>
    }
  } catch {
    errors.triggerJson = uiText.workflows.create.invalidJson
  }

  try {
    const parsed = JSON.parse(formState.graphJson || '{}')
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
      errors.graphJson = uiText.workflows.create.jsonObjectRequired
    } else {
      graph = parsed as Record<string, unknown>
    }
  } catch {
    errors.graphJson = uiText.workflows.create.invalidJson
  }

  if (Object.keys(errors).length > 0 || !trigger || !graph) {
    return {
      valid: false,
      errors,
    }
  }

  return {
    valid: true,
    errors,
    command: {
      key,
      name,
      description: description || undefined,
      status: formState.status,
      trigger,
      graph,
    },
  }
}

