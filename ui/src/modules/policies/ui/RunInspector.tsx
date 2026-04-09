import { uiText } from '../../../shared/constants/uiText'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { formatDateTime } from '../../../shared/lib/date'
import type { PolicyExecutionRunDetail } from '../lib/policySchemas'
import { formatPolicyLabel, runStatusLabel, runStatusTone } from '../lib/policiesDisplay'
import { StepTimeline } from './StepTimeline'

type RunInspectorProps = {
  detail: PolicyExecutionRunDetail
}

export function RunInspector({ detail }: RunInspectorProps) {
  return (
    <section className="space-y-4 text-sm">
      <div className="space-y-3">
        <DetailRow label={uiText.policies.runStatusLabel}>
          <StatusBadge label={runStatusLabel(detail.status)} tone={runStatusTone(detail.status)} />
        </DetailRow>

        {detail.reasonCode && (
          <DetailRow label={uiText.policies.runReasonCodeLabel}>
            <span className="font-mono text-xs">{detail.reasonCode}</span>
          </DetailRow>
        )}

        <DetailRow label={uiText.policies.runLeadLabel}>
          <span className="font-mono text-xs">{detail.sourceLeadId ?? '-'}</span>
        </DetailRow>

        <DetailRow label={uiText.policies.runPolicyLabel}>
          <span className="text-xs">{formatPolicyLabel(detail.policyKey, detail.policyVersion)}</span>
        </DetailRow>

        <DetailRow label={uiText.policies.runSourceLabel}>
          <span className="text-xs">{detail.source}</span>
        </DetailRow>

        {detail.eventId && (
          <DetailRow label={uiText.policies.runEventIdLabel}>
            <span className="font-mono text-xs">{detail.eventId}</span>
          </DetailRow>
        )}

        <DetailRow label={uiText.policies.runCreatedAtLabel}>
          <span className="font-mono text-xs">{formatDateTime(detail.createdAt)}</span>
        </DetailRow>
      </div>

      <div className="border-t border-[var(--color-border)] pt-3">
        <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
          {uiText.policies.runStepsTitle}
        </p>
        <StepTimeline steps={detail.steps} />
      </div>
    </section>
  )
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">{label}</p>
      <div>{children}</div>
    </div>
  )
}
