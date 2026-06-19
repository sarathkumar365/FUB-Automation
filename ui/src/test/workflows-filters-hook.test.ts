import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useWorkflowsFilters } from '@modules/workflows/ui/useWorkflowsFilters'
import type { WorkflowsPageSearchState } from '@modules/workflows/lib/workflowsSearchParams'

const base: WorkflowsPageSearchState = { status: undefined, page: 2, size: 20 }

function lastParams(spy: ReturnType<typeof vi.fn>): URLSearchParams {
  return spy.mock.calls.at(-1)?.[0] as URLSearchParams
}

describe('useWorkflowsFilters', () => {
  it('setStatus updates the draft without touching the URL', () => {
    const setSearchParams = vi.fn()
    const { result } = renderHook(() => useWorkflowsFilters(base, setSearchParams))
    act(() => result.current.setStatus('ACTIVE'))
    expect(result.current.draftFilters.status).toBe('ACTIVE')
    expect(setSearchParams).not.toHaveBeenCalled()
  })

  it('apply writes the status and resets the page (page 0 is default → omitted)', () => {
    const setSearchParams = vi.fn()
    const { result } = renderHook(() => useWorkflowsFilters(base, setSearchParams))
    act(() => result.current.setStatus('ACTIVE'))
    act(() => result.current.apply())
    const params = lastParams(setSearchParams)
    expect(params.get('status')).toBe('ACTIVE')
    expect(params.get('page')).toBeNull() // reset from 2 → 0 (default, not serialized)
  })

  it('reset clears the status back to ALL', () => {
    const setSearchParams = vi.fn()
    const { result } = renderHook(() => useWorkflowsFilters({ ...base, status: 'ACTIVE' }, setSearchParams))
    act(() => result.current.reset())
    expect(result.current.draftFilters.status).toBe('ALL')
    expect(lastParams(setSearchParams).get('status')).toBeNull()
  })

  it('reseeds the draft when the applied status changes out from under it (draft keying)', () => {
    const setSearchParams = vi.fn()
    const { result, rerender } = renderHook(({ state }) => useWorkflowsFilters(state, setSearchParams), {
      initialProps: { state: base },
    })
    act(() => result.current.setStatus('DRAFT'))
    expect(result.current.draftFilters.status).toBe('DRAFT')
    rerender({ state: { ...base, status: 'ACTIVE' } })
    expect(result.current.draftFilters.status).toBe('ACTIVE')
  })
})
