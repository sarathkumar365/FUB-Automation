/**
 * Section recipe — a labeled block of content.
 *
 * Per D3.1-a (workflow-builder-ui-audit, Slice 3): the caption is rendered
 * uppercase with letter-spacing in a muted tone. Matches the style already
 * used across the storyboard popover and workflow header strip, so adopting
 * this recipe causes no visual churn.
 *
 * Props:
 *   - `title`    — caption text (required). Always passed as a plain string;
 *                  i18n happens at the call site via `uiText.*`.
 *   - `children` — body content.
 *   - `action`   — optional right-aligned slot for a button / link / link-
 *                  like control. Kept small: caption row is not a full
 *                  header — use `DefinitionCard` if you need badge + action.
 *
 * Uses a `<section>` element with `aria-labelledby` pointing at the caption
 * so assistive tech exposes the caption as the section heading.
 */
import { useId, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface SectionProps {
  title: string
  children: ReactNode
  action?: ReactNode
  className?: string
}

export function Section({ title, children, action, className }: SectionProps) {
  const captionId = useId()
  return (
    <section aria-labelledby={captionId} className={cn('flex flex-col gap-2', className)}>
      <header className="flex items-center justify-between gap-2">
        <span
          id={captionId}
          className="text-[11px] font-semibold uppercase tracking-[0.08em] text-[var(--color-text-muted)]"
        >
          {title}
        </span>
        {action ? <div className="flex shrink-0 items-center">{action}</div> : null}
      </header>
      <div>{children}</div>
    </section>
  )
}
