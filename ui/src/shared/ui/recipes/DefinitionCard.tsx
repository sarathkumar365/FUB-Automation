/**
 * DefinitionCard recipe — a titled surface with optional badge + action
 * in the header, and a body slot.
 *
 * Per D3.5-c: header slots are title (required) + optional badge +
 * optional action. This matches the shape of the scene inspector popover
 * header (scene title + step-type badge + close button) and the workflow
 * header strip.
 *
 * Per D3.5a-c (workflow-builder catalog integration): when used inside
 * the workflow surfaces, the badge slot typically receives a `<Badge>`
 * whose text is the step-type / trigger-type `displayName` and whose
 * `title` attribute carries the id + description from the backend
 * catalog endpoint (step-types / trigger-types). Until that catalog
 * endpoint (backend task B1) ships, callers fall back to rendering the
 * raw id as the badge text with no tooltip. This recipe is
 * catalog-agnostic — it does not know about step types; the rule lives
 * at the call site. See `workflow-builder-ui-audit.md` D3.5a for the
 * decision rationale.
 *
 * Props:
 *   - `title`    — heading text (required; always a plain string).
 *   - `badge`    — optional ReactNode rendered next to the title.
 *   - `action`   — optional right-aligned slot (button, icon button,
 *                  menu trigger).
 *   - `children` — body content.
 */
import { useId, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface DefinitionCardProps {
  title: string
  badge?: ReactNode
  action?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
}

export function DefinitionCard({
  title,
  badge,
  action,
  children,
  className,
  bodyClassName,
}: DefinitionCardProps) {
  const titleId = useId()
  return (
    <section
      aria-labelledby={titleId}
      className={cn(
        'flex flex-col overflow-hidden rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-subtle)]',
        className,
      )}
    >
      <header className="flex items-center gap-3 border-b border-[var(--color-border)] px-4 py-3">
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <h3
            id={titleId}
            className="min-w-0 truncate text-sm font-semibold text-[var(--color-text)]"
          >
            {title}
          </h3>
          {badge ? <div className="shrink-0">{badge}</div> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </header>
      <div className={cn('p-4', bodyClassName)}>{children}</div>
    </section>
  )
}
