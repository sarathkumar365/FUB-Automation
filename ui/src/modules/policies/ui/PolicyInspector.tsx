import { uiText } from '../../../shared/constants/uiText'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import type { PolicyResponse } from '../lib/policySchemas'
import { policyStatusLabel, policyStatusTone } from '../lib/policiesDisplay'
import { BlueprintView } from './BlueprintView'

type PolicyInspectorProps = {
  policy: PolicyResponse
}

export function PolicyInspector({ policy }: PolicyInspectorProps) {
  return (
    <section className="space-y-4 text-sm">
      <div className="space-y-3">
        <DetailRow label={uiText.policies.policyDomainLabel}>
          <span className="font-mono text-xs">{policy.domain}</span>
        </DetailRow>

        <DetailRow label={uiText.policies.policyKeyLabel}>
          <span className="font-mono text-xs">{policy.policyKey}</span>
        </DetailRow>

        <DetailRow label={uiText.policies.policyStatusLabel}>
          <StatusBadge label={policyStatusLabel(policy.status)} tone={policyStatusTone(policy.status)} />
        </DetailRow>

        <DetailRow label={uiText.policies.policyVersionLabel}>
          <span className="font-mono text-xs">v{policy.version}</span>
        </DetailRow>

        <DetailRow label={uiText.policies.policyEnabledLabel}>
          <span className="text-xs">{policy.enabled ? 'Yes' : 'No'}</span>
        </DetailRow>
      </div>

      <div className="border-t border-[var(--color-border)] pt-3">
        <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
          {uiText.policies.policyBlueprintTitle}
        </p>
        <BlueprintView blueprint={policy.blueprint} />
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
