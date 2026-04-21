/**
 * Shared layout/size constants for the storyboard tab inspector popover
 * and its subcomponents. Centralised here so the popover shell and the
 * extracted inspector subcomponents agree on spacing without drifting.
 */

export const POPOVER_WIDTH = 340
export const POPOVER_OFFSET = 16
export const POPOVER_MAX_HEIGHT = 480
export const POPOVER_MIN_HEIGHT = 120
export const POPOVER_EDGE_PADDING = 16
/** Absolute offset (from the top/right edge) of the floating close button. */
export const CLOSE_BUTTON_OFFSET = 8
export const INSPECTOR_PADDING_Y = 18
export const INSPECTOR_PADDING_X = 20
/** Fixed width of the `dt` label column in the config grid. */
export const CONFIG_LABEL_COLUMN_WIDTH = 110
/** Tailwind class for the JsonViewer maxHeight when rendering structured config values. */
export const CONFIG_JSON_MAX_HEIGHT_CLASS = 'max-h-48'
