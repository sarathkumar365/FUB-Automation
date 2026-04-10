import * as Dialog from '@radix-ui/react-dialog'
import { useState } from 'react'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import { Input } from '../../../shared/ui/input'
import { Select } from '../../../shared/ui/select'
import type { PolicyResponse } from '../lib/policySchemas'

type PolicyFormModalProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  policy?: PolicyResponse
  onSubmit: (data: PolicyFormData) => void
  isPending: boolean
}

export type PolicyFormData = {
  domain: string
  policyKey: string
  enabled: boolean
  blueprint: Record<string, unknown>
}

const DEFAULT_CLAIM_DELAY = 5
const DEFAULT_COMMS_DELAY = 10

export function PolicyFormModal({ open, onOpenChange, policy, onSubmit, isPending }: PolicyFormModalProps) {
  const isEdit = policy !== undefined
  const existingBlueprint = isEdit ? parseExistingBlueprint(policy.blueprint) : null

  const [domain, setDomain] = useState(policy?.domain ?? '')
  const [policyKey, setPolicyKey] = useState(policy?.policyKey ?? '')
  const [enabled, setEnabled] = useState(policy?.enabled ?? true)
  const [claimDelay, setClaimDelay] = useState(existingBlueprint?.claimDelay ?? DEFAULT_CLAIM_DELAY)
  const [commsDelay, setCommsDelay] = useState(existingBlueprint?.commsDelay ?? DEFAULT_COMMS_DELAY)
  const [actionType, setActionType] = useState(existingBlueprint?.actionType ?? 'REASSIGN')
  const [targetId, setTargetId] = useState(existingBlueprint?.targetId ?? '')

  const canSubmit =
    domain.trim().length > 0 &&
    policyKey.trim().length > 0 &&
    claimDelay > 0 &&
    commsDelay > 0 &&
    targetId.toString().trim().length > 0 &&
    !isPending

  const handleSubmit = () => {
    const blueprint = buildBlueprint(claimDelay, commsDelay, actionType, Number(targetId))
    onSubmit({
      domain: domain.trim(),
      policyKey: policyKey.trim(),
      enabled,
      blueprint,
    })
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(90vw,520px)] -translate-x-1/2 -translate-y-1/2 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-lg focus:outline-none">
          <Dialog.Title className="text-lg font-semibold text-[var(--color-text)]">
            {isEdit ? `Edit Policy — ${policy.policyKey} v${policy.version}` : 'New Policy'}
          </Dialog.Title>

          <div className="mt-4 space-y-4">
            {/* Identity fields */}
            <div className="grid grid-cols-2 gap-3">
              <FieldGroup label="Domain">
                <Input
                  value={domain}
                  onChange={(e) => setDomain(e.target.value)}
                  disabled={isEdit}
                  placeholder="ASSIGNMENT"
                />
              </FieldGroup>
              <FieldGroup label="Policy Key">
                <Input
                  value={policyKey}
                  onChange={(e) => setPolicyKey(e.target.value)}
                  disabled={isEdit}
                  placeholder="FOLLOW_UP_SLA"
                />
              </FieldGroup>
            </div>

            {/* Enabled */}
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => setEnabled(e.target.checked)}
                className="h-4 w-4 rounded border-[var(--color-border)]"
              />
              <span className="text-[var(--color-text)]">Enabled</span>
            </label>

            {/* Steps */}
            <div className="space-y-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">Steps</p>

              <div className="rounded-md border border-[var(--color-border)] bg-[var(--color-surface-alt)] p-3 space-y-3">
                <StepField
                  stepNumber={1}
                  label="Check Claim"
                  delayValue={claimDelay}
                  onDelayChange={setClaimDelay}
                />
                <StepField
                  stepNumber={2}
                  label="Check Communication"
                  delayValue={commsDelay}
                  onDelayChange={setCommsDelay}
                />

                <div className="border-t border-[var(--color-border)] pt-3">
                  <p className="mb-2 text-xs font-medium text-[var(--color-text)]">3. Execute Action (if SLA breached)</p>
                  <div className="grid grid-cols-2 gap-3">
                    <FieldGroup label={uiText.policies.filterStatusLabel}>
                      <Select
                        value={actionType}
                        onChange={(e) => setActionType(e.target.value)}
                        className="w-full"
                      >
                        <option value="REASSIGN">{uiText.policies.actionReassign}</option>
                        <option value="MOVE_TO_POND">{uiText.policies.actionMoveToPond}</option>
                      </Select>
                    </FieldGroup>
                    <FieldGroup label={actionType === 'REASSIGN' ? 'Target User ID' : 'Target Pond ID'}>
                      <Input
                        type="number"
                        value={targetId}
                        onChange={(e) => setTargetId(e.target.value)}
                        placeholder="12345"
                        min={1}
                      />
                    </FieldGroup>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              {uiText.dialog.cancel}
            </Button>
            <Button onClick={handleSubmit} disabled={!canSubmit}>
              {isPending ? 'Saving...' : isEdit ? `Save as v${policy.version + 1}` : 'Create'}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function StepField({
  stepNumber,
  label,
  delayValue,
  onDelayChange,
}: {
  stepNumber: number
  label: string
  delayValue: number
  onDelayChange: (v: number) => void
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <p className="text-xs font-medium text-[var(--color-text)]">
        {stepNumber}. {label}
      </p>
      <div className="flex items-center gap-1.5">
        <Input
          type="number"
          value={delayValue}
          onChange={(e) => onDelayChange(Math.max(1, Number(e.target.value) || 1))}
          className="w-20 text-center"
          min={1}
        />
        <span className="text-xs text-[var(--color-text-muted)]">min</span>
      </div>
    </div>
  )
}

function FieldGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="text-xs text-[var(--color-text-muted)]">{label}</span>
      {children}
    </label>
  )
}

function buildBlueprint(
  claimDelay: number,
  commsDelay: number,
  actionType: string,
  targetId: number,
): Record<string, unknown> {
  return {
    templateKey: 'ASSIGNMENT_FOLLOWUP_SLA_V1',
    steps: [
      { type: 'WAIT_AND_CHECK_CLAIM', delayMinutes: claimDelay },
      { type: 'WAIT_AND_CHECK_COMMUNICATION', delayMinutes: commsDelay, dependsOn: 'WAIT_AND_CHECK_CLAIM' },
      { type: 'ON_FAILURE_EXECUTE_ACTION', dependsOn: 'WAIT_AND_CHECK_COMMUNICATION' },
    ],
    actionConfig: {
      actionType,
      ...(actionType === 'REASSIGN' ? { targetUserId: targetId } : { targetPondId: targetId }),
    },
  }
}

function parseExistingBlueprint(blueprint: Record<string, unknown>) {
  const steps = blueprint['steps']
  if (!Array.isArray(steps)) return null

  const claimStep = steps.find((s) => s?.type === 'WAIT_AND_CHECK_CLAIM')
  const commsStep = steps.find((s) => s?.type === 'WAIT_AND_CHECK_COMMUNICATION')
  const actionConfig = blueprint['actionConfig'] as Record<string, unknown> | undefined

  return {
    claimDelay: typeof claimStep?.delayMinutes === 'number' ? claimStep.delayMinutes : DEFAULT_CLAIM_DELAY,
    commsDelay: typeof commsStep?.delayMinutes === 'number' ? commsStep.delayMinutes : DEFAULT_COMMS_DELAY,
    actionType: typeof actionConfig?.actionType === 'string' ? actionConfig.actionType : 'REASSIGN',
    targetId:
      typeof actionConfig?.targetUserId === 'number'
        ? String(actionConfig.targetUserId)
        : typeof actionConfig?.targetPondId === 'number'
          ? String(actionConfig.targetPondId)
          : '',
  }
}
