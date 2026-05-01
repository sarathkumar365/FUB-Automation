/**
 * FilterBar — horizontal filter row with optional trailing action cluster.
 *
 * Used by list surfaces that let the user narrow a table by a handful of
 * criteria. Centralises the flex-row + actions pattern so each list page
 * does not re-implement wrap / alignment rules.
 *
 * Props:
 *   - `children` — filter controls (labels + selects etc.). Wrapped in a
 *     flex region that wraps on narrow screens.
 *   - `actions`  — optional trailing action cluster (Apply / Reset, etc.).
 *     Pushed to the right edge via `ml-auto`.
 *   - `bordered` — default `true`. When `false`, the bar drops its border /
 *     surface / padding so it can sit inside a parent card that already
 *     supplies the containing chrome (e.g. `WorkflowDetailPage/RunsTab`
 *     filter row, which lives above a bordered data-table card).
 *
 * All tokens are sourced from `tokens.css` — no literal colors here.
 */
import type { PropsWithChildren, ReactNode } from 'react'
import { cn } from '../lib/cn'

type FilterBarProps = PropsWithChildren<{
  actions?: ReactNode
  bordered?: boolean
  className?: string
}>

export function FilterBar({ actions, children, bordered = true, className }: FilterBarProps) {
  return (
    <div
      data-recipe="filter-bar"
      className={cn(
        bordered && 'rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-3',
        className,
      )}
    >
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex flex-wrap items-center gap-2">{children}</div>
        {actions ? <div className="ml-auto flex items-center gap-2">{actions}</div> : null}
      </div>
    </div>
  )
}
