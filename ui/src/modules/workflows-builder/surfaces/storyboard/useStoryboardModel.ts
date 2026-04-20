/**
 * Shared model + layout hook for storyboard surfaces.
 *
 * Both the authoring Storyboard (builder store) and the read-only Storyboard
 * tab on the workflow detail page need the same derived `StoryboardModel` and
 * `StoryboardLayout`. Colocating the memoized derivation here keeps the two
 * call sites aligned and lets consumers reuse the layout for overlays (e.g.
 * the scene inspector popover) without recomputing Dagre twice.
 */
import { useMemo } from 'react'
import { graphToStoryboard, type StoryboardModel } from '../../model/graphAdapters'
import { layoutStoryboard, type StoryboardLayout } from '../../model/layoutEngine'
import type { Graph } from '../../state/runtimeContract'

export interface UseStoryboardModelResult {
  model: StoryboardModel
  layout: StoryboardLayout
}

export function useStoryboardModel(
  graph: Graph,
  trigger: Record<string, unknown> | null,
): UseStoryboardModelResult {
  const model = useMemo(() => graphToStoryboard(graph, trigger), [graph, trigger])
  const layout = useMemo(() => layoutStoryboard(model), [model])
  return { model, layout }
}
