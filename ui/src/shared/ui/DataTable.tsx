import type { ReactNode } from 'react'
import { useMemo } from 'react'
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
}

export function DataTable<T>({ columns, rows, getRowKey, loading = false, emptyMessage }: DataTableProps<T>) {
  const normalizedRows = useMemo(() => rows, [rows])

  if (loading) {
    return <p className="text-sm text-[var(--color-text-muted)]">{uiText.states.loadingMessage}</p>
  }

  if (normalizedRows.length === 0) {
    return <p className="text-sm text-[var(--color-text-muted)]">{emptyMessage ?? uiText.states.emptyMessage}</p>
  }

  return (
    <div className="overflow-x-auto rounded-md border border-[var(--color-border)] bg-[var(--color-surface)]">
      <table className="min-w-full text-left text-sm">
        <thead className="bg-[var(--color-surface-alt)] text-[var(--color-text-muted)]">
          <tr>
            {columns.map((column) => (
              <th key={column.key} className="px-3 py-2 font-medium">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {normalizedRows.map((row) => (
            <tr key={getRowKey(row)} className="border-t border-[var(--color-border)]">
              {columns.map((column) => (
                <td key={column.key} className={`px-3 py-2 ${column.className ?? ''}`}>
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export type { ColumnDef, DataTableProps }
