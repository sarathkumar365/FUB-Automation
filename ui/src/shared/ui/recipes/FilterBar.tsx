/**
 * FilterBar recipe — a horizontal row of filter controls + action cluster.
 *
 * Purpose:
 *   List / detail surfaces that let the user narrow a table by a handful of
 *   criteria end up re-implementing the same flex-row pattern (label + select,
 *   label + select, apply, reset). Extracting it here centralises the spacing
 *   contract and stops each caller from reinventing alignment / wrap rules.
 *
 * Props:
 *   - `children` — filter controls. Each control is rendered inline in the
 *     row's main flex region. Callers remain responsible for the label +
 *     control pairing (so RJSF-ish schema freedom is preserved).
 *   - `actions`  — optional trailing action cluster (Apply / Reset, etc.).
 *     Rendered after the filter controls on the same row.
 *   - `bordered` — when `true` (default `false`), the bar is wrapped in the
 *     standard bordered / surface-filled card treatment. Use `false` inside
 *     a card that already has its own border; `true` when the bar is the
 *     outermost surface on its row (e.g. standalone list filters).
 *
 * Tokens: border / surface / text pulled from `tokens.css` — no literals.
 */
import { type ReactNode } from 'react'
import { cn } from '../../lib/cn'

export interface FilterBarProps {
  children: ReactNode
  actions?: ReactNode
  bordered?: boolean
  className?: string
}

export function FilterBar({ children, actions, bordered = false, className }: FilterBarProps) {
  return (
    <div
      data-recipe="filter-bar"
      className={cn(
        'flex flex-wrap items-end gap-3',
        bordered && 'rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-3',
        className,
      )}
    >
      {children}
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </div>
  )
}
