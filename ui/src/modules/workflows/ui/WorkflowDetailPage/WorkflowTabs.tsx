/**
 * Underline-style tab bar for the workflow detail page.
 *
 * Two tabs: Storyboard (the new visualizer) and Runs. Reads / writes the
 * current tab via `useSearchParams` using the existing `tab` query param.
 * The actual tab content lives in the page and switches on `state.tab`;
 * this component only drives the search-param state, so we render hidden
 * `TabsContent` placeholders to keep Radix happy without duplicating
 * content here.
 */
import { useSearchParams } from 'react-router-dom'
import { uiText } from '../../../../shared/constants/uiText'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../../../shared/ui/Tabs'
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

  const handleValueChange = (value: string) => {
    const next = value === 'runs' ? 'runs' : 'definition'
    setSearchParams(createWorkflowDetailSearchParamsFromState({ ...state, tab: next }))
  }

  return (
    <Tabs value={state.tab} onValueChange={handleValueChange}>
      <TabsList aria-label={uiText.workflows.detailTabsAriaLabel}>
        {TABS.map((tab) => (
          <TabsTrigger key={tab.id} value={tab.id}>
            {tab.label}
          </TabsTrigger>
        ))}
      </TabsList>
      {/* Hidden content placeholders keep Radix from warning. Real content
          is rendered by the page based on state.tab. */}
      {TABS.map((tab) => (
        <TabsContent key={tab.id} value={tab.id} className="hidden" />
      ))}
    </Tabs>
  )
}
