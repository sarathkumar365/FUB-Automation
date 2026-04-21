/**
 * Shared model + layout hook for storyboard surfaces.
 *
 * Both the authoring Storyboard (builder store) and the read-only Storyboard
 * tab on the workflow detail page need the same derived `StoryboardModel`,
 * `StoryboardLayout`, terminal placements, and viewport box. Colocating the
 * memoized derivation here keeps the two call sites aligned and lets
 * consumers reuse the layout for overlays (e.g. the scene inspector popover)
 * without recomputing Dagre twice.
 */
import { useMemo } from 'react'
import { graphToStoryboard, type StoryboardModel } from '../../model/graphAdapters'
import { layoutStoryboard, type StoryboardLayout } from '../../model/layoutEngine'
import type { Graph } from '../../state/runtimeContract'
import {
  CANVAS_HEIGHT_TRAILING_SLACK,
  MIN_CANVAS_HEIGHT,
  MIN_CANVAS_WIDTH,
} from './constants'
import {
  computeTerminalPlacements,
  computeViewportBox,
  type TerminalPlacement,
  type ViewportBox,
} from './viewport'

export interface CanvasSize {
  /** Rendered SVG width after applying the minimum-canvas-width floor. */
  width: number
  /** Rendered SVG height after applying the trailing slack + floor. */
  height: number
}

export interface UseStoryboardModelResult {
  model: StoryboardModel
  layout: StoryboardLayout
  terminalPlacements: TerminalPlacement[]
  viewport: ViewportBox
  /** Canonical canvas size for all surfaces that render this storyboard. Use
   *  this instead of recomputing `Math.max(viewport.width, 320)` at call
   *  sites to keep the builder + detail surfaces in lockstep. */
  canvasSize: CanvasSize
}

export function useStoryboardModel(
  graph: Graph,
  trigger: Record<string, unknown> | null,
): UseStoryboardModelResult {
  const model = useMemo(() => graphToStoryboard(graph, trigger), [graph, trigger])
  const layout = useMemo(() => layoutStoryboard(model), [model])
  const terminalPlacements = useMemo(
    () => computeTerminalPlacements(model, layout),
    [model, layout],
  )
  const viewport = useMemo(
    () => computeViewportBox({ layout, terminalPlacements }),
    [layout, terminalPlacements],
  )
  const canvasSize = useMemo<CanvasSize>(
    () => ({
      width: Math.max(viewport.width, MIN_CANVAS_WIDTH),
      height: Math.max(viewport.height + CANVAS_HEIGHT_TRAILING_SLACK, MIN_CANVAS_HEIGHT),
    }),
    [viewport],
  )
  return { model, layout, terminalPlacements, viewport, canvasSize }
}
