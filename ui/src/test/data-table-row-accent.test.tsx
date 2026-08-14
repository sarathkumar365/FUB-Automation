import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DataTable, type ColumnDef } from '@shared/ui/DataTable'

type Row = { id: number; name: string }

const columns: ColumnDef<Row>[] = [{ key: 'name', header: 'Name', render: (r) => <span>{r.name}</span> }]
const rows: Row[] = [
  { id: 1, name: 'alpha' },
  { id: 2, name: 'beta' },
]

describe('DataTable rowAccent', () => {
  it('applies an inline leading rail on the first cell when rowAccent is provided', () => {
    render(<DataTable columns={columns} rows={rows} getRowKey={(r) => r.id} rowAccent={() => 'var(--color-status-bad)'} />)

    const cell = screen.getByText('alpha').closest('td')
    expect(cell?.style.borderLeftWidth).toBe('3px')
    expect(cell?.getAttribute('style')).toContain('border-left-style: solid')
  })

  it('uses no inline rail when rowAccent is omitted (default brand-on-hover behavior)', () => {
    render(<DataTable columns={columns} rows={rows} getRowKey={(r) => r.id} />)

    const cell = screen.getByText('alpha').closest('td')
    expect(cell?.style.borderLeftWidth).toBe('')
  })

  it('lets the selection rail win over the accent on the selected row', () => {
    render(
      <DataTable
        columns={columns}
        rows={rows}
        getRowKey={(r) => r.id}
        selectedRowKey={1}
        rowAccent={() => 'var(--color-status-bad)'}
      />,
    )

    // Selected row → no inline accent (falls back to the brand selection railClass).
    expect(screen.getByText('alpha').closest('td')?.style.borderLeftWidth).toBe('')
    // Non-selected row → inline accent rail still applies.
    expect(screen.getByText('beta').closest('td')?.style.borderLeftWidth).toBe('3px')
  })
})
