import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { WorkflowTabs } from '../modules/workflows/ui/WorkflowDetailPage/WorkflowTabs'

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="probe-search">{location.search}</div>
}

function renderAt(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              <WorkflowTabs />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('WorkflowTabs', () => {
  it('defaults to the definition tab when no search param is present', () => {
    renderAt('/workflows/1')
    const definitionTab = screen.getByRole('tab', { name: 'Storyboard' })
    expect(definitionTab.getAttribute('data-state')).toBe('active')
    const runsTab = screen.getByRole('tab', { name: 'Runs' })
    expect(runsTab.getAttribute('data-state')).toBe('inactive')
  })

  it('activates the runs tab when the URL has tab=runs', () => {
    renderAt('/workflows/1?tab=runs')
    const runsTab = screen.getByRole('tab', { name: 'Runs' })
    expect(runsTab.getAttribute('data-state')).toBe('active')
    const definitionTab = screen.getByRole('tab', { name: 'Storyboard' })
    expect(definitionTab.getAttribute('data-state')).toBe('inactive')
  })

  it('exposes a tablist with an accessible aria-label', () => {
    renderAt('/workflows/1')
    const tablist = screen.getByRole('tablist', { name: 'Workflow detail sections' })
    expect(tablist).toBeInTheDocument()
  })

  it('updates the URL search params when a different tab is clicked', async () => {
    const user = userEvent.setup()
    renderAt('/workflows/1')
    await user.click(screen.getByRole('tab', { name: 'Runs' }))
    expect(screen.getByTestId('probe-search').textContent).toContain('tab=runs')
  })

  it('clears the tab search param when clicking back to the default tab', async () => {
    const user = userEvent.setup()
    renderAt('/workflows/1?tab=runs')
    await user.click(screen.getByRole('tab', { name: 'Storyboard' }))
    const search = screen.getByTestId('probe-search').textContent ?? ''
    expect(search).not.toContain('tab=runs')
  })
})
