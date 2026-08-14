import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { PortsContext } from '@app/portsContextValue'
import { PersonsPage } from '@modules/persons/ui/PersonsPage'
import type { AppPorts } from '@platform/container'
import type { PersonFeedItem, PersonFeedPage } from '@shared/types/person'
import { uiText } from '@shared/constants/uiText'

function person(over?: Partial<PersonFeedItem>): PersonFeedItem {
  return {
    id: 1,
    sourceSystem: 'fub',
    sourcePersonId: 'p_1',
    status: 'ACTIVE',
    snapshot: { firstName: 'Ada', lastName: 'Lovelace' },
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    lastSyncedAt: '2026-01-03T00:00:00Z',
    ...over,
  }
}

function setup(opts?: {
  listPersons?: AppPorts['personsPort']['listPersons']
  initialPath?: string
}) {
  const listPersons =
    opts?.listPersons ??
    vi.fn(async (): Promise<PersonFeedPage> => ({ items: [person()], nextCursor: null, serverTime: 't' }))

  const ports = {
    adminWebhookPort: {},
    processedCallsPort: {},
    webhookStreamPort: {},
    personsPort: { listPersons, getPersonSummary: vi.fn() },
    workflowPort: {},
    workflowRunPort: {},
  } as unknown as AppPorts

  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })

  render(
    <PortsContext.Provider value={ports}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[opts?.initialPath ?? '/admin-ui/persons']}>
          <Routes>
            <Route path="/admin-ui/persons" element={<PersonsPage />} />
            <Route path="/admin-ui/persons/:id" element={<div>person detail stub</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </PortsContext.Provider>,
  )

  return { listPersons }
}

describe('PersonsPage', () => {
  it('renders person rows from the query', async () => {
    setup()
    expect(await screen.findByText('p_1')).toBeInTheDocument()
  })

  it('navigates to the person detail on row click', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(await screen.findByRole('button', { name: `${uiText.persons.rowAriaLabelPrefix} p_1` }))
    expect(await screen.findByText('person detail stub')).toBeInTheDocument()
  })

  it('applies the status filter to the query', async () => {
    const user = userEvent.setup()
    const { listPersons } = setup()
    await screen.findByText('p_1')

    await user.selectOptions(screen.getByLabelText(uiText.persons.filterStatusLabel), 'ARCHIVED')
    await user.click(screen.getByRole('button', { name: uiText.filters.apply }))

    await waitFor(() =>
      expect(listPersons).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'ARCHIVED', cursor: undefined })),
    )
  })

  it('pages forward with the returned cursor', async () => {
    const user = userEvent.setup()
    const listPersons = vi.fn(async () => ({ items: [person()], nextCursor: 'cursor-2', serverTime: 't' }))
    setup({ listPersons })
    await screen.findByText('p_1')

    await user.click(screen.getByRole('button', { name: uiText.persons.paginationNextAria }))

    await waitFor(() =>
      expect(listPersons).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: 'cursor-2' })),
    )
  })

  it('shows the empty state when there are no rows', async () => {
    setup({ listPersons: vi.fn(async () => ({ items: [], nextCursor: null, serverTime: 't' })) })
    expect(await screen.findByText(uiText.persons.tableEmptyMessage)).toBeInTheDocument()
  })

  it('shows the error state when the query fails', async () => {
    setup({
      listPersons: vi.fn(async () => {
        throw new Error('boom')
      }),
    })
    expect(await screen.findByText(uiText.states.errorMessage)).toBeInTheDocument()
  })
})
