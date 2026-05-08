/**
 * Shared layout/size constants for the storyboard tab inspector popover
 * and its subcomponents. Centralised here so the popover shell and the
 * extracted inspector subcomponents agree on spacing without drifting.
 */

/** Popover width per D4.7-c. Bumped from 340 → 420 so stacked long
 *  values (URLs, templating, long plain strings) and card-per-transition
 *  layouts have room to breathe. */
export const POPOVER_WIDTH = 420
export const POPOVER_OFFSET = 16
/** Popover max-height per D4.8-a. Bumped from 480 → 560 to scale with
 *  the wider footprint; inner body still handles overflow via scroll. */
export const POPOVER_MAX_HEIGHT = 560
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
