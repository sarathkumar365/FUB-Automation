/**
 * ClampedText recipe — clamp a long text block to N lines, with a
 * "Show more" / "Show less" toggle when the content actually overflows.
 *
 * Per D4.4-a + D4.10-b (workflow-builder-ui-audit, Slice 4): used for
 * long plain strings in the scene inspector popover. Clamps at 4 lines
 * by default; the toggle only renders when the clamped block would
 * overflow, measured after layout.
 *
 * Uses `-webkit-line-clamp` + `display: -webkit-box` for the clamp
 * itself (widely supported; matches how Chrome DevTools, GitHub, Linear
 * do line-limited previews). A `useLayoutEffect` pass compares
 * `scrollHeight` vs. `clientHeight` in the clamped state to decide
 * whether the toggle should render — no toggle for content that fits.
 *
 * The component renders its own `<button>` for the toggle; it is a
 * plain in-flow element so it sits naturally under the text rather than
 * floating. Inherits the popover's font; no color override.
 *
 * Props:
 *   - `text`        — string content to clamp.
 *   - `maxLines`    — default 4 (D4.10-b). Overridable per call site.
 *   - `className`   — forwarded to the outer wrapper.
 *   - `monospace`   — render in `var(--font-mono)` for code-ish content.
 *   - `showMoreLabel` / `showLessLabel` — override toggle labels if a
 *     call site needs different wording. Default to the uiText.common
 *     strings.
 */
import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
} from 'react'
import { cn } from '../../lib/cn'
import { uiText } from '../../constants/uiText'

export interface ClampedTextProps {
  text: string
  maxLines?: number
  className?: string
  monospace?: boolean
  showMoreLabel?: string
  showLessLabel?: string
}

export function ClampedText({
  text,
  maxLines = 4,
  className,
  monospace = false,
  showMoreLabel,
  showLessLabel,
}: ClampedTextProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [expanded, setExpanded] = useState(false)
  const [overflowing, setOverflowing] = useState(false)

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    // Measure in the clamped state: toggle is only meaningful if there is
    // more content than the clamp shows.
    setOverflowing(el.scrollHeight - el.clientHeight > 1)
  }, [text, maxLines])

  // Re-check after fonts load or the container resizes.
  useEffect(() => {
    const el = ref.current
    if (!el || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => {
      if (expanded) return
      setOverflowing(el.scrollHeight - el.clientHeight > 1)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [expanded])

  const clampedStyle: CSSProperties = expanded
    ? { whiteSpace: 'pre-wrap' }
    : {
        display: '-webkit-box',
        WebkitLineClamp: maxLines,
        WebkitBoxOrient: 'vertical',
        overflow: 'hidden',
        whiteSpace: 'pre-wrap',
      }

  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <div
        ref={ref}
        data-testid="clamped-text-body"
        data-expanded={expanded ? 'true' : 'false'}
        className={cn(
          'min-w-0 break-words text-sm text-[var(--color-text)]',
          monospace ? 'font-mono' : undefined,
        )}
        style={clampedStyle}
      >
        {text}
      </div>
      {overflowing ? (
        <button
          type="button"
          onClick={() => setExpanded((prev) => !prev)}
          className="self-start text-xs font-medium text-[var(--color-brand)] hover:underline"
        >
          {expanded
            ? (showLessLabel ?? uiText.common.showLess)
            : (showMoreLabel ?? uiText.common.showMore)}
        </button>
      ) : null}
    </div>
  )
}
