import { useMemo, useState } from 'react'
import type { SetURLSearchParams } from 'react-router-dom'
import {
  createWorkflowsSearchParamsFromState,
  toWorkflowsDraftFilters,
  type WorkflowsFilterDraft,
  type WorkflowsPageSearchState,
} from '../lib/workflowsSearchParams'

/**
 * Owns the workflows list filter draft. The draft is keyed by the applied status so it reseeds when the
 * applied filter changes out from under it (e.g. via back/forward) instead of going stale.
 */
export function useWorkflowsFilters(searchState: WorkflowsPageSearchState, setSearchParams: SetURLSearchParams) {
  const filterDraftKey = useMemo(() => searchState.status ?? 'ALL', [searchState.status])
  const [draftFilterState, setDraftFilterState] = useState<{ key: string; value: WorkflowsFilterDraft }>(() => ({
    key: filterDraftKey,
    value: toWorkflowsDraftFilters(searchState),
  }))
  const draftFilters =
    draftFilterState.key === filterDraftKey ? draftFilterState.value : toWorkflowsDraftFilters(searchState)

  const setStatus = (status: WorkflowsFilterDraft['status']) => {
    setDraftFilterState({ key: filterDraftKey, value: { status } })
  }

  const apply = () => {
    const nextState: WorkflowsPageSearchState = {
      status: draftFilters.status === 'ALL' ? undefined : draftFilters.status,
      page: 0,
      size: searchState.size,
      selectedKey: undefined,
    }
    setSearchParams(createWorkflowsSearchParamsFromState(nextState))
  }

  const reset = () => {
    setDraftFilterState({ key: filterDraftKey, value: { status: 'ALL' } })
    setSearchParams(createWorkflowsSearchParamsFromState({ page: 0, size: searchState.size }))
  }

  return { draftFilters, setStatus, apply, reset }
}
