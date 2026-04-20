/**
 * Workflow detail page — visualizer-first layout.
 *
 * Orchestrates:
 *   - Data fetching (detail + versions)
 *   - Mutation state via `useWorkflowDetailActions`
 *   - Tab routing via `useSearchParams`
 *   - Shell inspector registration for the version history list
 *   - Render of the header strip, tabs, and the active tab contents
 *
 * The page short-circuits with loading/error states before delegating the
 * "ready" branch to a child component so `useWorkflowDetailActions` can
 * assume a non-null workflow.
 */
import { useMemo } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../../app/useShellRegionRegistration'
import { uiText } from '../../../../shared/constants/uiText'
import { ConfirmDialog } from '../../../../shared/ui/ConfirmDialog'
import { ErrorState } from '../../../../shared/ui/ErrorState'
import { LoadingState } from '../../../../shared/ui/LoadingState'
import { PageCard } from '../../../../shared/ui/PageCard'
import { PageHeader } from '../../../../shared/ui/PageHeader'
import { useWorkflowDetailQuery } from '../../data/useWorkflowDetailQuery'
import { useWorkflowVersionsQuery } from '../../data/useWorkflowVersionsQuery'
import type { WorkflowResponse, WorkflowVersionSummary } from '../../lib/workflowSchemas'
import { parseWorkflowDetailSearchParams } from '../../lib/workflowsSearchParams'
import { WorkflowEditModal } from '../WorkflowEditModal'
import { WorkflowVersionList } from '../WorkflowVersionList'
import { RunsTab } from './RunsTab'
import { StoryboardTab } from './StoryboardTab'
import { WorkflowHeaderStrip } from './WorkflowHeaderStrip'
import { WorkflowTabs } from './WorkflowTabs'
import { useWorkflowDetailActions } from './lib/useWorkflowDetailActions'

export function WorkflowDetailPage() {
  const { key } = useParams<{ key: string }>()

  const detailQuery = useWorkflowDetailQuery(key)
  const versionsQuery = useWorkflowVersionsQuery(key)

  if (detailQuery.isPending) {
    return <LoadingState />
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className="space-y-4">
        <PageHeader title={uiText.workflows.detailTitle} subtitle={uiText.workflows.detailSubtitle} />
        <PageCard title={uiText.states.errorTitle}>
          <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflows.detailNotFound}</p>
        </PageCard>
      </div>
    )
  }

  return (
    <WorkflowDetailReady
      workflow={detailQuery.data}
      versionsData={versionsQuery.data ?? null}
      versionsIsPending={versionsQuery.isPending}
      versionsIsError={versionsQuery.isError}
    />
  )
}

interface WorkflowDetailReadyProps {
  workflow: WorkflowResponse
  versionsData: WorkflowVersionSummary[] | null
  versionsIsPending: boolean
  versionsIsError: boolean
}

function WorkflowDetailReady({
  workflow,
  versionsData,
  versionsIsPending,
  versionsIsError,
}: WorkflowDetailReadyProps) {
  const [searchParams] = useSearchParams()
  const detailSearchState = useMemo(
    () => parseWorkflowDetailSearchParams(searchParams),
    [searchParams],
  )

  const actions = useWorkflowDetailActions({ workflow })
  const setRollbackTarget = actions.setRollbackTarget
  const isRollbackPending = actions.isRollbackPending

  const inspectorBody = useMemo(() => {
    if (versionsIsPending) return <LoadingState />
    if (versionsIsError) return <ErrorState message={uiText.states.errorMessage} />
    if (!versionsData || versionsData.length === 0) {
      return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.emptyMessage}</p>
    }
    return (
      <WorkflowVersionList
        versions={versionsData}
        currentVersionNumber={workflow.versionNumber ?? null}
        onRequestRollback={(toVersion) => setRollbackTarget(toVersion)}
        isRollbackPending={isRollbackPending}
      />
    )
  }, [
    versionsData,
    versionsIsPending,
    versionsIsError,
    workflow.versionNumber,
    setRollbackTarget,
    isRollbackPending,
  ])

  const inspectorRegion = useMemo(
    () => ({ title: uiText.workflows.detailVersionsTitle, body: inspectorBody }),
    [inspectorBody],
  )

  useShellRegionRegistration({ panel: null, inspector: inspectorRegion })

  return (
    <div className="space-y-5">
      <WorkflowHeaderStrip
        workflow={workflow}
        isAnyActionPending={actions.isAnyActionPending}
        onEdit={() => actions.editModal.setOpen(true)}
        onValidate={actions.onValidate}
        onActivate={actions.onActivate}
        onDeactivate={actions.onDeactivate}
        onArchive={actions.onArchive}
      />

      <WorkflowTabs />

      {detailSearchState.tab === 'runs' ? (
        <RunsTab workflow={workflow} />
      ) : (
        <StoryboardTab
          workflow={workflow}
          validationState={actions.validationState}
          onValidate={() => void actions.onValidate()}
          onDismissValidation={actions.dismissValidation}
          isValidationPending={actions.validationState.mode === 'pending'}
        />
      )}

      <WorkflowEditModal
        key={`${workflow.key}-${workflow.version ?? 'none'}-${actions.editModal.open ? 'open' : 'closed'}`}
        open={actions.editModal.open}
        onOpenChange={actions.editModal.setOpen}
        workflow={workflow}
        onSubmit={actions.onEditSubmit}
        isSubmitting={actions.isUpdatePending}
      />
      <ConfirmDialog
        open={actions.rollbackTarget !== null}
        title={uiText.workflows.rollbackConfirmTitle}
        description={uiText.workflows.rollbackConfirmDescription}
        onOpenChange={(open) => {
          if (!open) {
            actions.setRollbackTarget(null)
          }
        }}
        onConfirm={() => void actions.onRollback()}
      />
    </div>
  )
}
