/**
 * Storyboard geometry constants — single barrel for values shared between
 * the SVG renderer, layout engine, viewport sizer, and any outer surface that
 * needs to reserve space for the canvas.
 *
 * Keep this file literal-only. No helpers, no React, no conditional logic —
 * just re-exports + the pair of canvas floor constants used by consumers.
 */
export { CHIP_FONT_SIZE, CHIP_CHAR_WIDTH, CHIP_PADDING_X, estimateChipWidth } from './chipMetrics'
export type { EstimateChipWidthOptions } from './chipMetrics'

export {
  TERMINAL_PILL_GAP,
  TERMINAL_PILL_ROW_SPACING,
  TERMINAL_PILL_MIN_WIDTH,
} from './viewport'

export { SCENE_WIDTH, SCENE_HEIGHT } from '../../model/layoutEngine'

/**
 * Minimum SVG canvas width in user-space pixels. Mirrors the historical floor
 * applied by both `StoryboardViewer` and the workflow detail surface — chosen
 * so the dot-grid and edges have somewhere to live even when the graph is
 * trivial (e.g. a single entry scene). Consumers should take
 * `Math.max(viewport.width, MIN_CANVAS_WIDTH)`.
 */
export const MIN_CANVAS_WIDTH = 320

/**
 * Minimum SVG canvas height floor. Matches the legacy viewer floor; the extra
 * slack beyond `viewport.height` keeps the last rank of scenes from kissing
 * the bottom edge.
 */
export const MIN_CANVAS_HEIGHT = 240

/**
 * Vertical padding added to `viewport.height` before applying the floor so
 * the graph breathes below its last rank. Kept here so callers don't drift.
 */
export const CANVAS_HEIGHT_TRAILING_SLACK = 24
