import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useSceneSelection } from '../modules/workflows/ui/WorkflowDetailPage/StoryboardTab/useSceneSelection'

describe('useSceneSelection', () => {
  it('starts with a null selection', () => {
    const { result } = renderHook(() => useSceneSelection())
    expect(result.current.selectedSceneId).toBeNull()
  })

  it('selects and clears the current scene id', () => {
    const { result } = renderHook(() => useSceneSelection())
    act(() => result.current.select('node-abc'))
    expect(result.current.selectedSceneId).toBe('node-abc')
    act(() => result.current.clear())
    expect(result.current.selectedSceneId).toBeNull()
  })
})
