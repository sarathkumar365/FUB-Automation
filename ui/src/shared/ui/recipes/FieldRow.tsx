/**
 * FieldRow recipe — a labeled value with automatic layout.
 *
 * Per D3.2-a + D4.3-b (workflow-builder-ui-audit, Slice 3/4):
 *   - Short scalars (booleans, numbers, enums) and short strings ≤
 *     `FIELD_ROW_LONG_THRESHOLD` characters render inline as
 *     `label · value`.
 *   - Long strings, multi-line strings, and non-primitive ReactNodes
 *     render stacked: label on top, value on a full-width line below.
 *   - Callers can force either layout via the `layout` prop.
 *
 * The threshold constant is exported so Slice 4 surfaces (scene inspector
 * config rows) can override it per field kind if a specific value type
 * wants a different boundary (e.g. URLs always stack regardless of length).
 *
 * Props:
 *   - `label`  — always a plain string (i18n at the call site).
 *   - `value`  — string / number / boolean / ReactNode. `null` /
 *                `undefined` renders an em-dash as a visual placeholder.
 *   - `layout` — `'inline' | 'stacked' | 'auto'` (default `'auto'`).
 */
import { isValidElement, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

/** Character count at which a plain string flips from inline to stacked in
 *  `layout='auto'`. See D4.3-b. */
export const FIELD_ROW_LONG_THRESHOLD = 60

export type FieldRowLayout = 'inline' | 'stacked' | 'auto'

export interface FieldRowProps {
  label: string
  value: ReactNode
  layout?: FieldRowLayout
  className?: string
}

function resolveLayout(value: ReactNode, layout: FieldRowLayout): 'inline' | 'stacked' {
  if (layout !== 'auto') return layout
  if (value === null || value === undefined) return 'inline'
  if (typeof value === 'boolean' || typeof value === 'number') return 'inline'
  if (typeof value === 'string') {
    if (value.includes('\n')) return 'stacked'
    return value.length > FIELD_ROW_LONG_THRESHOLD ? 'stacked' : 'inline'
  }
  // ReactNode / element / fragment / array — we can't measure, stack.
  if (isValidElement(value) || Array.isArray(value)) return 'stacked'
  return 'stacked'
}

function renderValue(value: ReactNode): ReactNode {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  return value
}

export function FieldRow({ label, value, layout = 'auto', className }: FieldRowProps) {
  const resolved = resolveLayout(value, layout)
  const rendered = renderValue(value)

  if (resolved === 'inline') {
    return (
      <div
        className={cn(
          'grid items-baseline gap-2',
          'grid-cols-[minmax(0,110px)_minmax(0,1fr)]',
          className,
        )}
      >
        <span className="truncate text-xs font-medium text-[var(--color-text-muted)]">{label}</span>
        <span className="min-w-0 break-words text-sm text-[var(--color-text)]">{rendered}</span>
      </div>
    )
  }

  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <span className="text-[11px] font-semibold uppercase tracking-[0.06em] text-[var(--color-text-muted)]">
        {label}
      </span>
      <div className="min-w-0 break-words text-sm text-[var(--color-text)]">{rendered}</div>
    </div>
  )
}
