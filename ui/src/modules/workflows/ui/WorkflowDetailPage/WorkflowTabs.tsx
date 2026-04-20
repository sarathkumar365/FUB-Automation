/**
 * Underline-style tab bar for the workflow detail page.
 *
 * Two tabs: Storyboard (the new visualizer) and Runs. Active tab is marked
 * with a 2px brand-colored bottom border and a filled label; inactive tabs
 * render with a muted label. Reads / writes the current tab via
 * `useSearchParams` using the existing `tab` query param.
 */
import { useSearchParams } from 'react-router-dom'
import { uiText } from '../../../../shared/constants/uiText'
import {
  createWorkflowDetailSearchParamsFromState,
  parseWorkflowDetailSearchParams,
  type WorkflowDetailTab,
} from '../../lib/workflowsSearchParams'

type Tab = { id: WorkflowDetailTab; label: string }

const TABS: Tab[] = [
  { id: 'definition', label: uiText.workflows.detailTabStoryboard },
  { id: 'runs', label: uiText.workflows.detailTabRuns },
]

export function WorkflowTabs() {
  const [searchParams, setSearchParams] = useSearchParams()
  const state = parseWorkflowDetailSearchParams(searchParams)

  const select = (tab: WorkflowDetailTab) => {
    setSearchParams(createWorkflowDetailSearchParamsFromState({ ...state, tab }))
  }

  return (
    <div
      aria-label="Workflow detail sections"
      className="flex items-center gap-6 border-b border-[var(--color-border)]"
    >
      {TABS.map((tab) => {
        const active = state.tab === tab.id
        return (
          <button
            key={tab.id}
            type="button"
            aria-selected={active}
            onClick={() => select(tab.id)}
            className={
              active
                ? 'relative -mb-px border-b-2 border-[var(--color-brand)] px-1 py-2 text-sm font-semibold text-[var(--color-text)]'
                : 'relative -mb-px border-b-2 border-transparent px-1 py-2 text-sm font-medium text-[var(--color-text-muted)] hover:text-[var(--color-text)]'
            }
          >
            {tab.label}
          </button>
        )
      })}
    </div>
  )
}
