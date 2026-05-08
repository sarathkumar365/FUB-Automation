import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { WorkflowsSubNav } from '../modules/workflows/ui/WorkflowsSubNav'
import { routes } from '../shared/constants/routes'
import { uiText } from '../shared/constants/uiText'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <WorkflowsSubNav />
    </MemoryRouter>,
  )
}

describe('WorkflowsSubNav', () => {
  it('marks Definitions active on the workflows list route', () => {
    renderAt(routes.workflows)

    const definitions = screen.getByRole('link', { name: uiText.workflows.subNavDefinitions })
    const runs = screen.getByRole('link', { name: uiText.workflows.subNavRuns })

    expect(definitions.getAttribute('aria-current')).toBe('page')
    expect(runs.getAttribute('aria-current')).toBeNull()
  })

  it('marks Runs active on the workflow-runs list route', () => {
    renderAt(routes.workflowRuns)

    const definitions = screen.getByRole('link', { name: uiText.workflows.subNavDefinitions })
    const runs = screen.getByRole('link', { name: uiText.workflows.subNavRuns })

    expect(runs.getAttribute('aria-current')).toBe('page')
    expect(definitions.getAttribute('aria-current')).toBeNull()
  })

  it('does not mark any sub-tab active on detail routes (sub-nav is list-only per N1.4)', () => {
    renderAt(routes.workflowDetail('wf_sample'))

    const definitions = screen.getByRole('link', { name: uiText.workflows.subNavDefinitions })
    const runs = screen.getByRole('link', { name: uiText.workflows.subNavRuns })

    expect(definitions.getAttribute('aria-current')).toBeNull()
    expect(runs.getAttribute('aria-current')).toBeNull()
  })

  it('exposes an aria-label for the nav landmark', () => {
    renderAt(routes.workflows)
    expect(screen.getByRole('navigation', { name: uiText.workflows.subNavAriaLabel })).toBeInTheDocument()
  })

  it('links point at the canonical list URLs (preserves bookmarks per N1.2)', () => {
    renderAt(routes.workflows)

    const definitions = screen.getByRole('link', { name: uiText.workflows.subNavDefinitions })
    const runs = screen.getByRole('link', { name: uiText.workflows.subNavRuns })

    expect(definitions.getAttribute('href')).toBe(routes.workflows)
    expect(runs.getAttribute('href')).toBe(routes.workflowRuns)
  })
})
