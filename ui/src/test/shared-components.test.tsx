import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ConfirmDialog } from '../shared/ui/ConfirmDialog'
import { DataTable, type ColumnDef } from '../shared/ui/DataTable'
import { EmptyState } from '../shared/ui/EmptyState'
import { ErrorState } from '../shared/ui/ErrorState'
import { FilterBar } from '../shared/ui/FilterBar'
import { InspectorPanel } from '../shared/ui/InspectorPanel'
import { LoadingState } from '../shared/ui/LoadingState'
import { PageHeader } from '../shared/ui/PageHeader'
import { StatusBadge } from '../shared/ui/StatusBadge'

describe('shared UI components', () => {
  it('renders page header, states, and status badge', () => {
    render(
      <div>
        <PageHeader title="Header" subtitle="Sub" />
        <LoadingState />
        <EmptyState />
        <ErrorState />
        <StatusBadge tone="success" label="Ready" />
      </div>,
    )

    expect(screen.getByText('Header')).toBeInTheDocument()
    expect(screen.getByText('Please wait while data is being prepared.')).toBeInTheDocument()
    expect(screen.getByText('No results found')).toBeInTheDocument()
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
    expect(screen.getByText('Ready')).toBeInTheDocument()
  })

  it('renders data table rows with stable keys contract', () => {
    type Row = { id: string; name: string }
    const columns: ColumnDef<Row>[] = [
      { key: 'name', header: 'Name', render: (row) => row.name },
    ]

    render(<DataTable columns={columns} rows={[{ id: '1', name: 'Alpha' }]} getRowKey={(row) => row.id} />)

    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByText('Alpha')).toBeInTheDocument()
  })

  it('renders filter bar, inspector panel, and confirm dialog', () => {
    render(
      <div>
        <FilterBar actions={<button type="button">Action</button>}>
          <span>Filter Input</span>
        </FilterBar>
        <InspectorPanel title="Inspector">Details</InspectorPanel>
        <ConfirmDialog
          open
          title="Confirm"
          description="Are you sure"
          onOpenChange={() => undefined}
          onConfirm={() => undefined}
        />
      </div>,
    )

    expect(screen.getByText('Filter Input')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument()
    expect(screen.getByText('Are you sure')).toBeInTheDocument()
  })
})
