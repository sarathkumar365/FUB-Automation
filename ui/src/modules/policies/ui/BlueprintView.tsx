import { uiText } from '../../../shared/constants/uiText'
import { actionTypeLabel, stepTypeLabel } from '../lib/policiesDisplay'

type BlueprintViewProps = {
  blueprint: Record<string, unknown>
}

type BlueprintStep = {
  type: string
  delayMinutes?: number
  dependsOn?: string
}

type ActionConfig = {
  actionType: string
  targetUserId?: number
  targetPondId?: number
}

export function BlueprintView({ blueprint }: BlueprintViewProps) {
  const steps = parseSteps(blueprint)
  const actionConfig = parseActionConfig(blueprint)

  if (steps.length === 0) {
    return (
      <pre className="max-h-40 overflow-auto rounded-md border border-[var(--color-border)] bg-[var(--color-surface-alt)] p-2 text-xs text-[var(--color-text)]">
        {safeJson(blueprint)}
      </pre>
    )
  }

  return (
    <div className="space-y-0">
      {steps.map((step, index) => (
        <div key={index} className="flex gap-3">
          <div className="flex flex-col items-center">
            <div className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface-alt)]">
              <span className="text-[10px] font-semibold text-[var(--color-text-muted)]">{index + 1}</span>
            </div>
            {index < steps.length - 1 && <div className="w-px flex-1 bg-[var(--color-border)]" />}
          </div>

          <div className="pb-3">
            <p className="text-sm font-medium text-[var(--color-text)]">{stepTypeLabel(step.type as never)}</p>
            {step.delayMinutes !== undefined && (
              <p className="text-xs text-[var(--color-text-muted)]">
                Wait: {step.delayMinutes} min
              </p>
            )}
            {step.type === 'ON_FAILURE_EXECUTE_ACTION' && actionConfig && (
              <div className="mt-0.5 space-y-0.5">
                <p className="text-xs text-[var(--color-text-muted)]">
                  {actionTypeLabel(actionConfig.actionType)}
                </p>
                {actionConfig.targetUserId !== undefined && (
                  <p className="font-mono text-xs text-[var(--color-text-muted)]">
                    {uiText.policies.actionReassign}: {actionConfig.targetUserId}
                  </p>
                )}
                {actionConfig.targetPondId !== undefined && (
                  <p className="font-mono text-xs text-[var(--color-text-muted)]">
                    {uiText.policies.actionMoveToPond}: {actionConfig.targetPondId}
                  </p>
                )}
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}

function parseSteps(blueprint: Record<string, unknown>): BlueprintStep[] {
  const raw = blueprint['steps']
  if (!Array.isArray(raw)) return []
  return raw.filter(
    (s): s is BlueprintStep => typeof s === 'object' && s !== null && typeof (s as Record<string, unknown>)['type'] === 'string',
  )
}

function parseActionConfig(blueprint: Record<string, unknown>): ActionConfig | null {
  const raw = blueprint['actionConfig']
  if (typeof raw !== 'object' || raw === null) return null
  const obj = raw as Record<string, unknown>
  if (typeof obj['actionType'] !== 'string') return null
  return {
    actionType: obj['actionType'],
    targetUserId: typeof obj['targetUserId'] === 'number' ? obj['targetUserId'] : undefined,
    targetPondId: typeof obj['targetPondId'] === 'number' ? obj['targetPondId'] : undefined,
  }
}

function safeJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return '"[unserializable]"'
  }
}
