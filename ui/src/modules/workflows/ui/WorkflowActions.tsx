import { useState } from 'react'
import { uiText } from '../../../shared/constants/uiText'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog } from '../../../shared/ui/ConfirmDialog'

type WorkflowActionsProps = {
  canEdit: boolean
  canValidate: boolean
  canActivate: boolean
  canDeactivate: boolean
  canArchive: boolean
  isPending: boolean
  onEdit: () => void
  onValidate: () => Promise<void>
  onActivate: () => Promise<void>
  onDeactivate: () => Promise<void>
  onArchive: () => Promise<void>
}

export function WorkflowActions({
  canEdit,
  canValidate,
  canActivate,
  canDeactivate,
  canArchive,
  isPending,
  onEdit,
  onValidate,
  onActivate,
  onDeactivate,
  onArchive,
}: WorkflowActionsProps) {
  const [confirmState, setConfirmState] = useState<'activate' | 'deactivate' | 'archive' | null>(null)

  const handleConfirm = async () => {
    const action = confirmState
    setConfirmState(null)

    if (action === 'activate') {
      await onActivate()
    } else if (action === 'deactivate') {
      await onDeactivate()
    } else if (action === 'archive') {
      await onArchive()
    }
  }

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" size="sm" variant="outline" disabled={!canEdit || isPending} onClick={onEdit}>
          {uiText.workflows.actions.edit}
        </Button>
        <Button type="button" size="sm" variant="outline" disabled={!canValidate || isPending} onClick={() => void onValidate()}>
          {uiText.workflows.actions.validate}
        </Button>
        <Button type="button" size="sm" disabled={!canActivate || isPending} onClick={() => setConfirmState('activate')}>
          {uiText.workflows.actions.activate}
        </Button>
        <Button type="button" size="sm" variant="outline" disabled={!canDeactivate || isPending} onClick={() => setConfirmState('deactivate')}>
          {uiText.workflows.actions.deactivate}
        </Button>
        <Button type="button" size="sm" variant="destructive" disabled={!canArchive || isPending} onClick={() => setConfirmState('archive')}>
          {uiText.workflows.actions.archive}
        </Button>
      </div>

      <ConfirmDialog
        open={confirmState !== null}
        title={
          confirmState === 'activate'
            ? uiText.workflows.actions.activateConfirmTitle
            : confirmState === 'deactivate'
              ? uiText.workflows.actions.deactivateConfirmTitle
              : uiText.workflows.actions.archiveConfirmTitle
        }
        description={
          confirmState === 'activate'
            ? uiText.workflows.actions.activateConfirmDescription
            : confirmState === 'deactivate'
              ? uiText.workflows.actions.deactivateConfirmDescription
              : uiText.workflows.actions.archiveConfirmDescription
        }
        confirmLabel={uiText.dialog.confirm}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmState(null)
          }
        }}
        onConfirm={() => void handleConfirm()}
      />
    </>
  )
}

