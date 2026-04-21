/**
 * Header strip for the workflow detail page.
 *
 * Three rows:
 *   1. Breadcrumb on the left, action cluster on the right.
 *   2. H1 workflow name.
 *   3. Chip row — filled status badge + version chip + trigger chip.
 *
 * The meta line is a proper chip row (not prose) so it reads as a status
 * band. A thin bottom border separates the strip from the body content.
 */
import { Link } from 'react-router-dom'
import { routes } from '../../../../shared/constants/routes'
import { uiText } from '../../../../shared/constants/uiText'
import { StatusBadge } from '../../../../shared/ui/StatusBadge'
import {
  canActivateWorkflow,
  canArchiveWorkflow,
  canDeactivateWorkflow,
  canEditWorkflow,
  canValidateWorkflow,
  formatWorkflowStatus,
  getWorkflowStatusTone,
} from '../../lib/workflowsDisplay'
import type { WorkflowResponse } from '../../lib/workflowSchemas'
import { WorkflowActions } from '../WorkflowActions'

export interface WorkflowHeaderStripProps {
  workflow: WorkflowResponse
  isAnyActionPending: boolean
  onEdit: () => void
  onValidate: () => Promise<void>
  onActivate: () => Promise<void>
  onDeactivate: () => Promise<void>
  onArchive: () => Promise<void>
}

export function WorkflowHeaderStrip({
  workflow,
  isAnyActionPending,
  onEdit,
  onValidate,
  onActivate,
  onDeactivate,
  onArchive,
}: WorkflowHeaderStripProps) {
  const statusTone = getWorkflowStatusTone(workflow.status)
  const triggerType = readTriggerType(workflow.trigger)

  return (
    <header className="flex flex-col gap-3 border-b border-[var(--color-border)] pb-4">
      <div className="flex items-center justify-between gap-3">
        <nav
          aria-label="Breadcrumb"
          className="flex items-center gap-2 text-sm text-[var(--color-text-muted)]"
        >
          <Link
            className="text-[var(--color-brand)] underline-offset-2 hover:underline"
            to={routes.workflows}
          >
            {uiText.workflows.title}
          </Link>
          <span aria-hidden>{uiText.workflows.detailBreadcrumbSeparator}</span>
          <span className="font-mono text-[var(--color-text)]">{workflow.key}</span>
        </nav>
        <WorkflowActions
          canEdit={canEditWorkflow(workflow.status)}
          canValidate={canValidateWorkflow(workflow.status)}
          canActivate={canActivateWorkflow(workflow.status)}
          canDeactivate={canDeactivateWorkflow(workflow.status)}
          canArchive={canArchiveWorkflow(workflow.status)}
          isPending={isAnyActionPending}
          onEdit={onEdit}
          onValidate={onValidate}
          onActivate={onActivate}
          onDeactivate={onDeactivate}
          onArchive={onArchive}
        />
      </div>

      <h1 className="text-3xl font-semibold text-[var(--color-text)]">{workflow.name}</h1>

      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge label={formatWorkflowStatus(workflow.status)} tone={statusTone} />
        {workflow.versionNumber !== null ? (
          <span
            className="inline-flex h-[26px] items-center rounded-full px-2.5 font-mono text-xs"
            style={{
              background: 'rgba(15, 159, 184, 0.12)',
              color: 'var(--color-brand)',
              border: '1px solid rgba(15, 159, 184, 0.22)',
            }}
          >
            {uiText.workflows.detailVersionMetaPrefix}
            {workflow.versionNumber}
          </span>
        ) : null}
        {triggerType ? (
          <span
            className="inline-flex h-[26px] items-center gap-1 overflow-hidden rounded-full border border-[var(--color-border)] bg-[var(--color-surface-alt)] px-2.5 text-xs"
            data-testid="workflow-header-trigger-chip"
            style={{ maxWidth: 240, minWidth: 0 }}
            title={triggerType}
          >
            <span className="shrink-0 text-[var(--color-text-muted)]">
              {uiText.workflows.detailTriggerMetaPrefix} ·
            </span>
            <span
              className="font-mono text-[var(--color-text)]"
              style={{
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {triggerType}
            </span>
          </span>
        ) : null}
      </div>
    </header>
  )
}

function readTriggerType(trigger: Record<string, unknown> | null): string | null {
  if (!trigger) return null
  const candidate = trigger.type
  return typeof candidate === 'string' && candidate.length > 0 ? candidate : null
}
