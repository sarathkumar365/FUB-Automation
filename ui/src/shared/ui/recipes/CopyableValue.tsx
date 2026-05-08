/**
 * CopyableValue recipe — a value rendered alongside a hover-revealed
 * copy-to-clipboard button.
 *
 * Per D3.3-b: the copy button is hidden by default and revealed on hover
 * over the row, on keyboard focus of the row, or unconditionally on
 * touch devices (`@media (hover: none)`). Per D3.4-a: after a successful
 * copy, the button label swaps to "Copied" for 1.5s then reverts — same
 * feedback pattern as `JsonViewer` (intentional consistency).
 *
 * The value rendering is opinionated toward technical content: monospace
 * font, pre-wrapped so templating and URL strings don't break mid-token.
 * Callers that need a different visual (e.g. chip, link, styled code) can
 * pass a custom `display` node — only the copied payload is fixed.
 *
 * Props:
 *   - `value`   — the raw string that will be written to the clipboard.
 *   - `display` — optional custom ReactNode to render in place of the
 *                 default monospace block. The copy button still writes
 *                 `value`.
 *   - `label`   — accessible label for the copy button. Defaults to
 *                 `uiText.common.jsonCopy`.
 *
 * Does NOT wrap around anchors / links — see D4.5-c. URLs surface here as
 * plain strings with a copy affordance; no navigation.
 */
import { useCallback, useState, type ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { uiText } from '../../constants/uiText'
import { Button } from '../button'

const COPIED_FLASH_MS = 1500

export interface CopyableValueProps {
  value: string
  display?: ReactNode
  label?: string
  className?: string
}

export function CopyableValue({ value, display, label, className }: CopyableValueProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = useCallback(async () => {
    if (!value || !navigator?.clipboard?.writeText) return
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      setTimeout(() => setCopied(false), COPIED_FLASH_MS)
    } catch {
      // clipboard may be denied; silently no-op rather than toast
    }
  }, [value])

  const buttonLabel = label ?? uiText.common.jsonCopy

  return (
    <div
      className={cn(
        'group relative flex items-start gap-2 rounded-md border border-transparent focus-within:border-[var(--color-border)]',
        className,
      )}
    >
      <div
        className={cn(
          'min-w-0 flex-1 whitespace-pre-wrap break-words rounded-md bg-[var(--color-surface-alt)] px-2 py-1 font-mono text-xs text-[var(--color-text)]',
        )}
      >
        {display ?? value}
      </div>
      <Button
        type="button"
        size="sm"
        variant="ghost"
        onClick={handleCopy}
        disabled={!value}
        aria-label={buttonLabel}
        className={cn(
          'shrink-0 opacity-0 transition-opacity',
          'group-hover:opacity-100 focus-visible:opacity-100 group-focus-within:opacity-100',
          '[@media(hover:none)]:opacity-100',
        )}
      >
        {copied ? uiText.common.jsonCopied : buttonLabel}
      </Button>
    </div>
  )
}
