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

            return (
              <tr
                key={rowKey}
                // TODO: Replace row-level button semantics with a focusable cell control to preserve native table navigation semantics.
                className={[
                  'border-t border-[color-mix(in_srgb,var(--color-border),transparent_40%)] transition-colors',
                  onRowClick
                    ? 'cursor-pointer hover:bg-[var(--color-surface-alt)] hover:shadow-[inset_3px_0_0_var(--color-brand)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)] focus-visible:ring-offset-2 ring-offset-[var(--color-surface)]'
                    : '',
                  isSelected ? 'bg-[var(--color-brand-soft)] shadow-[inset_3px_0_0_var(--color-brand)]' : '',
                ].join(' ')}
                role={onRowClick ? 'button' : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                aria-label={onRowClick ? (getRowAriaLabel?.(row) ?? undefined) : undefined}
                aria-pressed={onRowClick ? isSelected : undefined}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={
                  onRowClick
                    ? (event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          onRowClick(row)
                        }
                      }
                    : undefined
                }
              >
                {columns.map((column) => (
                  <td key={column.key} className={`px-4 py-3 ${column.className ?? ''}`}>
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
