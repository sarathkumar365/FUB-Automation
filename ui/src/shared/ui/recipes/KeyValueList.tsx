/**
 * KeyValueList recipe — a list of label/value pairs.
 *
 * Per D3.7-a: defaults to a 2-column grid where labels align down the
 * left column and values down the right — scannable for short values.
 * Pass `variant='stacked'` (or the `stacked` boolean shorthand) to flip
 * every row to label-above-value; this is what narrow surfaces like the
 * scene inspector popover opt into for long values.
 *
 * `grid` variant lays out children directly and does NOT use FieldRow.
 * `stacked` variant uses `FieldRow` with `layout='stacked'` for each item.
 * Both variants feed from the same `items` array so callers can flip
 * between the two without changing data.
 *
 * Props:
 *   - `items`    — `[{ label, value }, ...]`. `value` accepts the same
 *                  shape as `FieldRow.value`.
 *   - `variant`  — `'grid' | 'stacked'` (default `'grid'`).
 *   - `stacked`  — shorthand for `variant='stacked'`.
 *   - `emptyState` — optional ReactNode rendered when `items` is empty.
 */
import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { FieldRow } from './FieldRow'

export interface KeyValueListItem {
  key?: string
  label: string
  value: ReactNode
}

export interface KeyValueListProps {
  items: KeyValueListItem[]
  variant?: 'grid' | 'stacked'
  stacked?: boolean
  emptyState?: ReactNode
  className?: string
}

function renderValue(value: ReactNode): ReactNode {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  return value
}

export function KeyValueList({
  items,
  variant,
  stacked,
  emptyState,
  className,
}: KeyValueListProps) {
  const mode = stacked ? 'stacked' : (variant ?? 'grid')

  if (items.length === 0) {
    return emptyState ? <div className={className}>{emptyState}</div> : null
  }

  if (mode === 'grid') {
    return (
      <dl
        className={cn(
          'grid items-baseline gap-x-3 gap-y-2',
          'grid-cols-[minmax(0,110px)_minmax(0,1fr)]',
          className,
        )}
      >
        {items.map((item, index) => (
          <div key={item.key ?? `${item.label}-${index}`} className="contents">
            <dt className="truncate text-xs font-medium text-[var(--color-text-muted)]">
              {item.label}
            </dt>
            <dd className="min-w-0 break-words text-sm text-[var(--color-text)]">
              {renderValue(item.value)}
            </dd>
          </div>
        ))}
      </dl>
    )
  }

  return (
    <div className={cn('flex flex-col gap-3', className)}>
      {items.map((item, index) => (
        <FieldRow
          key={item.key ?? `${item.label}-${index}`}
          label={item.label}
          value={item.value}
          layout="stacked"
        />
      ))}
    </div>
  )
}
