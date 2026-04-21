/**
 * Pure helpers for the storyboard viewport + terminal pill placement.
 *
 * Why this lives separately from `StoryboardViewer` / `TerminalPill`:
 *  - Terminal pills render outside Dagre's bounding box. The viewBox has to be
 *    extended to include them, otherwise pills on the right clip off-canvas
 *    and pills on the left would render at negative x with no space.
 *  - Both the viewer (to render) and the viewport calculation (to size the
 *    SVG) need identical per-terminal side + width math. Any drift between
 *    them produces cut-off or mis-aligned pills.
 *
 * The functions here are pure (no React), deterministic, and unit-tested so
 * the two render paths cannot drift.
 */
import type { StoryboardModel } from '../../model/graphAdapters'
import type { StoryboardLayout } from '../../model/layoutEngine'
import { estimateChipWidth } from './chipMetrics'

/** Horizontal gap between a scene's edge and the terminal pill. Mirrors the
 *  leader-line length inside `TerminalPill`. */
export const TERMINAL_PILL_GAP = 28
/** Vertical spacing between stacked terminal pills sharing the same parent. */
export const TERMINAL_PILL_ROW_SPACING = 26

/** Minimum rendered width for a terminal pill. Chosen so single-word labels
 *  (`ok → done`) still feel like a chip rather than a tight sticker. */
export const TERMINAL_PILL_MIN_WIDTH = 140

/**
 * Estimate the rendered width of a terminal pill for a given `resultCode ->
 * reason` label. Delegates to the shared chip estimator so this module, the
 * pill itself, and the viewport math cannot drift on the width formula.
 */
export function estimateTerminalPillWidth(resultCode: string, reason: string): number {
  const label = `${resultCode} → ${reason}`
  return estimateChipWidth(label, { minWidth: TERMINAL_PILL_MIN_WIDTH })
}

export interface TerminalPlacement {
  terminalId: string
  fromSceneId: string
  side: 'left' | 'right'
  pillWidth: number
  offsetY: number
  /** Horizontal extent (user-space px) farthest from the parent on the LEFT.
   *  For a right-side pill this equals the parent's left edge (no extension). */
  extentLeft: number
  /** Horizontal extent (user-space px) farthest from the parent on the RIGHT.
   *  For a left-side pill this equals the parent's right edge (no extension). */
  extentRight: number
  /** Positional metadata preserved so the renderer doesn't recompute. */
  index: number
  totalTerminals: number
}

/**
 * Decide a side (left/right) for each terminal based on its parent scene's
 * position relative to the graph's horizontal midline, then compute the pill
 * width and horizontal extent in user-space. The result is consumed by both
 * the viewer (to render pills on the correct side) and the viewport sizer
 * (to include pill extents in the viewBox).
 */
export function computeTerminalPlacements(
  model: StoryboardModel,
  layout: StoryboardLayout,
): TerminalPlacement[] {
  const centerX = computeGraphCenterX(layout)

  const totals = new Map<string, number>()
  for (const terminal of model.terminals) {
    totals.set(terminal.fromSceneId, (totals.get(terminal.fromSceneId) ?? 0) + 1)
  }

  const indices = new Map<string, number>()
  const placements: TerminalPlacement[] = []
  for (const terminal of model.terminals) {
    const parent = layout.scenes.get(terminal.fromSceneId)
    if (!parent) continue
    const index = indices.get(terminal.fromSceneId) ?? 0
    indices.set(terminal.fromSceneId, index + 1)
    const totalTerminals = totals.get(terminal.fromSceneId) ?? 1
    const side: 'left' | 'right' = parent.x <= centerX ? 'left' : 'right'
    const pillWidth = estimateTerminalPillWidth(terminal.resultCode, terminal.reason)
    const offsetY = (index - (totalTerminals - 1) / 2) * TERMINAL_PILL_ROW_SPACING
    const parentLeftEdge = parent.x - parent.width / 2
    const parentRightEdge = parent.x + parent.width / 2
    const extentLeft =
      side === 'left' ? parentLeftEdge - TERMINAL_PILL_GAP - pillWidth : parentLeftEdge
    const extentRight =
      side === 'right' ? parentRightEdge + TERMINAL_PILL_GAP + pillWidth : parentRightEdge
    placements.push({
      terminalId: terminal.id,
      fromSceneId: terminal.fromSceneId,
      side,
      pillWidth,
      offsetY,
      extentLeft,
      extentRight,
      index,
      totalTerminals,
    })
  }
  return placements
}

/** Returns the midpoint of the layout's occupied horizontal range. Using the
 *  actual scene coordinates rather than `layout.width / 2` keeps this robust
 *  to Dagre's margin offsets. */
export function computeGraphCenterX(layout: StoryboardLayout): number {
  if (layout.scenes.size === 0) return layout.width / 2
  let minX = Number.POSITIVE_INFINITY
  let maxX = Number.NEGATIVE_INFINITY
  for (const scene of layout.scenes.values()) {
    const left = scene.x - scene.width / 2
    const right = scene.x + scene.width / 2
    if (left < minX) minX = left
    if (right > maxX) maxX = right
  }
  return (minX + maxX) / 2
}

export interface ViewportBox {
  x: number
  y: number
  width: number
  height: number
}

export interface ComputeViewportBoxInput {
  layout: StoryboardLayout
  terminalPlacements: TerminalPlacement[]
  /** Extra horizontal slack to absorb edge label chips that poke slightly
   *  outside Dagre's bounding box. */
  labelPadding?: number
  /** Outer padding applied on every side of the viewBox. */
  padding?: number
}

/**
 * Extend Dagre's bounding box to include the horizontal extent of terminal
 * pills (which can fly left of `0` on the left branch) and a small padding
 * for bezier label chips.
 */
export function computeViewportBox({
  layout,
  terminalPlacements,
  labelPadding = 10,
  padding = 12,
}: ComputeViewportBoxInput): ViewportBox {
  let minX = 0 - labelPadding
  let maxX = layout.width + labelPadding
  for (const placement of terminalPlacements) {
    if (placement.extentLeft < minX) minX = placement.extentLeft
    if (placement.extentRight > maxX) maxX = placement.extentRight
  }
  const viewBoxX = minX - padding
  const viewBoxWidth = maxX - minX + padding * 2
  return {
    x: viewBoxX,
    y: 0,
    width: viewBoxWidth,
    height: layout.height + padding,
  }
}

