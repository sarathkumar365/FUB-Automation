import { formatDateTime } from '../../../shared/lib/date'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import type { WorkflowVersionSummary } from '../lib/workflowSchemas'
import { formatWorkflowStatus } from '../lib/workflowsDisplay'

type WorkflowVersionListProps = {
  versions: WorkflowVersionSummary[]
  currentVersionNumber: number | null
  onRequestRollback: (toVersion: number) => void
  isRollbackPending: boolean
}

export function WorkflowVersionList({
  versions,
  currentVersionNumber,
  onRequestRollback,
  isRollbackPending,
}: WorkflowVersionListProps) {
  if (versions.length === 0) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.emptyMessage}</p>
  }

  return (
    <div className="space-y-2">
      {versions.map((version) => {
        const versionNumber = version.versionNumber
        const isCurrent = versionNumber !== null && currentVersionNumber !== null && versionNumber === currentVersionNumber
        const canRollback = versionNumber !== null && !isCurrent

        return (
          <div key={`${version.versionNumber ?? 'none'}-${version.updatedAt}`} className="rounded border border-[var(--color-border)] p-2 text-sm">
            <div className="flex items-center justify-between gap-2">
              <div>
                <p className="font-medium">v{versionNumber ?? '-'}</p>
                <p className="text-[var(--color-text-muted)]">{formatWorkflowStatus(version.status)}</p>
                <p className="text-xs text-[var(--color-text-muted)]">{formatDateTime(version.createdAt)}</p>
              </div>
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={!canRollback || isRollbackPending}
                onClick={() => {
                  if (versionNumber !== null) {
                    onRequestRollback(versionNumber)
                  }
                }}
              >
                {uiText.workflows.actions.rollback}
              </Button>
            </div>
          </div>
        )
      })}
    </div>
  )
}

