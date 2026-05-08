/**
 * Hook that encapsulates the workflow-detail mutation state machine.
 *
 * Owns the scoped validation view state, the edit-modal open flag, and the
 * rollback target. Exposes thin async wrappers over the mutations so the
 * detail page view can stay purely presentational. The validation state is
 * scoped by workflow key so navigating to another workflow resets it.
 */
import { useCallback, useState } from 'react'
import { uiText } from '../../../../../shared/constants/uiText'
import { useNotify } from '../../../../../shared/notifications/useNotify'
import { useActivateWorkflowMutation } from '../../../data/useActivateWorkflowMutation'
import { useArchiveWorkflowMutation } from '../../../data/useArchiveWorkflowMutation'
import { useDeactivateWorkflowMutation } from '../../../data/useDeactivateWorkflowMutation'
import { useRollbackWorkflowMutation } from '../../../data/useRollbackWorkflowMutation'
import { useUpdateWorkflowMutation } from '../../../data/useUpdateWorkflowMutation'
import { useValidateWorkflowMutation } from '../../../data/useValidateWorkflowMutation'
import type { WorkflowResponse } from '../../../lib/workflowSchemas'

export type ValidationViewState =
  | { mode: 'idle' }
  | { mode: 'pending' }
  | { mode: 'valid' }
  | { mode: 'invalid'; errors: string[] }
  | { mode: 'error'; message: string }

type ScopedValidationState = { scopeKey: string } & ValidationViewState

type UpdateCommand = Parameters<ReturnType<typeof useUpdateWorkflowMutation>['mutateAsync']>[0]

export interface UseWorkflowDetailActionsArgs {
  workflow: WorkflowResponse
}

export interface UseWorkflowDetailActionsResult {
  onEditSubmit: (command: UpdateCommand) => Promise<void>
  onValidate: () => Promise<void>
  onActivate: () => Promise<void>
  onDeactivate: () => Promise<void>
  onArchive: () => Promise<void>
  onRollback: () => Promise<void>
  validationState: ValidationViewState
  dismissValidation: () => void
  editModal: { open: boolean; setOpen: (value: boolean) => void }
  rollbackTarget: number | null
  setRollbackTarget: (value: number | null) => void
  isAnyActionPending: boolean
  isUpdatePending: boolean
  isRollbackPending: boolean
}

export function useWorkflowDetailActions({
  workflow,
}: UseWorkflowDetailActionsArgs): UseWorkflowDetailActionsResult {
  const notify = useNotify()
  const key = workflow.key
  const updateMutation = useUpdateWorkflowMutation(key)
  const validateMutation = useValidateWorkflowMutation()
  const activateMutation = useActivateWorkflowMutation(key)
  const deactivateMutation = useDeactivateWorkflowMutation(key)
  const archiveMutation = useArchiveWorkflowMutation(key)
  const rollbackMutation = useRollbackWorkflowMutation(key)

  const [editOpen, setEditOpen] = useState(false)
  const [rollbackTarget, setRollbackTarget] = useState<number | null>(null)
  const [validationState, setValidationState] = useState<ScopedValidationState>({
    scopeKey: key,
    mode: 'idle',
  })

  const scoped: ValidationViewState =
    validationState.scopeKey === key
      ? stripScope(validationState)
      : { mode: 'idle' }

  const dismissValidation = useCallback(() => {
    setValidationState({ scopeKey: key, mode: 'idle' })
  }, [key])

  const onEditSubmit = useCallback(
    async (command: UpdateCommand) => {
      try {
        await updateMutation.mutateAsync(command)
        setEditOpen(false)
        notify.success(uiText.workflows.editSuccess)
      } catch {
        notify.error(uiText.workflows.editError)
      }
    },
    [updateMutation, notify],
  )

  const onValidate = useCallback(async () => {
    setValidationState({ scopeKey: key, mode: 'pending' })
    try {
      const result = await validateMutation.mutateAsync({
        trigger: workflow.trigger ?? {},
        graph: workflow.graph ?? {},
      })
      if (result.valid) {
        setValidationState({ scopeKey: key, mode: 'valid' })
        notify.success(uiText.workflows.validateSuccess)
      } else {
        setValidationState({ scopeKey: key, mode: 'invalid', errors: result.errors })
        notify.warning(uiText.workflows.validateInvalid)
      }
    } catch {
      setValidationState({ scopeKey: key, mode: 'error', message: uiText.workflows.validateError })
      notify.error(uiText.workflows.validateError)
    }
  }, [key, validateMutation, workflow.trigger, workflow.graph, notify])

  const onActivate = useCallback(async () => {
    try {
      await activateMutation.mutateAsync()
      notify.success(uiText.workflows.activateSuccess)
    } catch {
      notify.error(uiText.workflows.activateError)
    }
  }, [activateMutation, notify])

  const onDeactivate = useCallback(async () => {
    try {
      await deactivateMutation.mutateAsync()
      notify.success(uiText.workflows.deactivateSuccess)
    } catch {
      notify.error(uiText.workflows.deactivateError)
    }
  }, [deactivateMutation, notify])

  const onArchive = useCallback(async () => {
    try {
      await archiveMutation.mutateAsync()
      notify.success(uiText.workflows.archiveSuccess)
    } catch {
      notify.error(uiText.workflows.archiveError)
    }
  }, [archiveMutation, notify])

  const onRollback = useCallback(async () => {
    if (rollbackTarget === null) return
    const target = rollbackTarget
    setRollbackTarget(null)
    try {
      await rollbackMutation.mutateAsync(target)
      notify.success(uiText.workflows.rollbackSuccess)
    } catch {
      notify.error(uiText.workflows.rollbackError)
    }
  }, [rollbackTarget, rollbackMutation, notify])

  const isAnyActionPending =
    updateMutation.isPending ||
    validateMutation.isPending ||
    activateMutation.isPending ||
    deactivateMutation.isPending ||
    archiveMutation.isPending ||
    rollbackMutation.isPending

  return {
    onEditSubmit,
    onValidate,
    onActivate,
    onDeactivate,
    onArchive,
    onRollback,
    validationState: scoped,
    dismissValidation,
    editModal: { open: editOpen, setOpen: setEditOpen },
    rollbackTarget,
    setRollbackTarget,
    isAnyActionPending,
    isUpdatePending: updateMutation.isPending,
    isRollbackPending: rollbackMutation.isPending,
  }
}

function stripScope(state: ScopedValidationState): ValidationViewState {
  if (state.mode === 'invalid') return { mode: 'invalid', errors: state.errors }
  if (state.mode === 'error') return { mode: 'error', message: state.message }
  return { mode: state.mode }
}
