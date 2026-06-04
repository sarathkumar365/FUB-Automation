import type { ReactNode } from 'react'
import { uiText } from '../constants/uiText'

type ColumnDef<T> = {
  key: string
  header: string
  render: (row: T) => ReactNode
  className?: string
}

type DataTableProps<T> = {
  columns: ColumnDef<T>[]
  rows: T[]
  getRowKey: (row: T) => string | number
  loading?: boolean
  emptyMessage?: string
  onRowClick?: (row: T) => void
  selectedRowKey?: string | number | null
  getRowAriaLabel?: (row: T) => string
}

export function DataTable<T>({
  columns,
  rows,
  getRowKey,
  loading = false,
  emptyMessage,
  onRowClick,
  selectedRowKey = null,
  getRowAriaLabel,
}: DataTableProps<T>) {
  if (loading) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.loadingMessage}</p>
  }

  if (rows.length === 0) {
    return <p className="text-sm text-[var(--color-text-muted)]">{emptyMessage ?? uiText.states.emptyMessage}</p>
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)]">
      <table className="min-w-full text-left text-sm">
        <thead className="bg-[var(--color-surface-alt)] text-[var(--color-text-muted)]">
          <tr className="border-b border-[var(--color-border)]">
            {columns.map((column) => (
              <th key={column.key} className="px-4 py-3 text-[11px] font-semibold uppercase tracking-[0.05em]">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const rowKey = getRowKey(row)
            const isSelected = selectedRowKey !== null && selectedRowKey === rowKey
            const interactive = Boolean(onRowClick)

            const railClass = isSelected
              ? 'border-l-[3px] border-l-[var(--color-brand)]'
              : interactive
                ? 'border-l-[3px] border-l-transparent group-hover:border-l-[var(--color-brand)]'
                : 'border-l-[3px] border-l-transparent'

            return (
              <tr
                key={rowKey}
                className={[
                  'group border-t border-[color-mix(in_srgb,var(--color-border),transparent_40%)] transition-colors',
                  interactive
                    ? 'cursor-pointer focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-[var(--color-brand)]'
                    : '',
                  interactive && !isSelected ? 'hover:bg-[var(--color-surface-alt)]' : '',
                  isSelected ? 'bg-[var(--color-brand-soft)]' : '',
                ].join(' ')}
                role={interactive ? 'button' : undefined}
                tabIndex={interactive ? 0 : undefined}
                aria-label={interactive ? (getRowAriaLabel?.(row) ?? undefined) : undefined}
                aria-pressed={interactive ? isSelected : undefined}
                onClick={interactive ? () => onRowClick?.(row) : undefined}
                onKeyDown={
                  interactive
                    ? (event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          onRowClick?.(row)
                        }
                      }
                    : undefined
                }
              >
                {columns.map((column, colIndex) => (
                  <td
                    key={column.key}
                    className={`px-4 py-3 ${colIndex === 0 ? railClass : ''} ${column.className ?? ''}`}
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export type { ColumnDef, DataTableProps }
