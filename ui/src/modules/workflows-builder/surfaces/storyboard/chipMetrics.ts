/**
 * Shared chip width estimator for SVG pills rendered on the storyboard.
 *
 * Why this exists: terminal pills and exit-edge labels each had their own
 * ad-hoc `length * 6 + 20` formula that produced widths too small for the
 * rendered text, so long labels overflowed past the chip border. Both pills
 * now route through this single estimator, and the viewport math imports the
 * same helper so the viewBox reservation matches what the pills actually
 * render at.
 *
 * The character-width factor is intentionally generous — the chips render in
 * JetBrains Mono at 11px, where real glyph advance runs just under 7px, but
 * anti-aliased ascenders and the `→` separator push effective width higher.
 * 7.2 absorbs that without producing absurdly wide chips for short labels
 * (for which `minWidth` dominates anyway).
 */

export const CHIP_FONT_SIZE = 11
export const CHIP_CHAR_WIDTH = 7.2
/** Total horizontal padding inside a chip (10px left + 10px right). */
export const CHIP_PADDING_X = 20

export interface EstimateChipWidthOptions {
  minWidth?: number
}

export function estimateChipWidth(
  text: string,
  options: EstimateChipWidthOptions = {},
): number {
  const minWidth = options.minWidth ?? 60
  const raw = text.length * CHIP_CHAR_WIDTH + CHIP_PADDING_X
  return Math.max(minWidth, Math.ceil(raw))
}
