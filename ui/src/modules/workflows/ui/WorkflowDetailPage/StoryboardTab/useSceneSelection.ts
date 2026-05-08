/**
 * Local scene-selection state for the read-only workflow detail storyboard.
 *
 * The detail page does not mount the builder zustand store, so selection is
 * tracked in component-local state. Exposed as a hook so it can be tested
 * in isolation.
 */
import { useCallback, useState } from 'react'

export interface UseSceneSelectionResult {
  selectedSceneId: string | null
  select: (id: string) => void
  clear: () => void
}

export function useSceneSelection(): UseSceneSelectionResult {
  const [selectedSceneId, setSelectedSceneId] = useState<string | null>(null)

  const select = useCallback((id: string) => setSelectedSceneId(id), [])
  const clear = useCallback(() => setSelectedSceneId(null), [])

  return { selectedSceneId, select, clear }
}
