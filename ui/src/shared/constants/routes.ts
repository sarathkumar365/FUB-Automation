export type AppNavKey = 'webhooks' | 'processedCalls' | 'leads' | 'workflows'

export const routes = {
  root: '/',
  adminUi: '/admin-ui',
  dashboard: '/admin-ui',
  webhooks: '/admin-ui/webhooks',
  processedCalls: '/admin-ui/processed-calls',
  leads: '/admin-ui/leads',
  leadDetail: (sourceLeadId: string) => `/admin-ui/leads/${encodeURIComponent(sourceLeadId)}`,
  workflows: '/admin-ui/workflows',
  workflowDetail: (key: string) => `/admin-ui/workflows/${encodeURIComponent(key)}`,
  workflowBuilderNew: '/admin-ui/workflows/new',
  workflowBuilderEdit: (key: string) => `/admin-ui/workflows/${encodeURIComponent(key)}/edit`,
  workflowRuns: '/admin-ui/workflow-runs',
  workflowRunDetail: (runId: number) => `/admin-ui/workflow-runs/${encodeURIComponent(String(runId))}`,
  sessionDisabled: '/admin-ui/session-disabled',
} as const

/**
 * Nav items are the *canonical* sidebar entries. Each entry's `matchPaths`
 * list declares every route prefix that should keep the nav item visually
 * "active". This lets a single sidebar entry cover multiple routes — in
 * particular, `Workflows` stays active across both the workflows
 * definitions list (`/workflows`) and the runs list (`/workflow-runs`),
 * which sit under a shared sub-tab bar inside the Workflows domain
 * (see `ui/Docs/workflow-ux-audit.md` D-Nav.1 / N1.2).
 */
type NavItem = {
  key: AppNavKey
  to: string
  railLabel: string
  matchPaths: readonly string[]
}

export const appNavItems: readonly NavItem[] = [
  {
    key: 'webhooks',
    to: routes.webhooks,
    railLabel: 'WH',
    matchPaths: [routes.webhooks],
  },
  {
    key: 'processedCalls',
    to: routes.processedCalls,
    railLabel: 'PC',
    matchPaths: [routes.processedCalls],
  },
  {
    key: 'leads',
    to: routes.leads,
    railLabel: 'LD',
    // Active on both the list (/admin-ui/leads) and detail
    // (/admin-ui/leads/:sourceLeadId) routes.
    matchPaths: [routes.leads],
  },
  {
    key: 'workflows',
    to: routes.workflows,
    railLabel: 'WF',
    // Active on both the Definitions sub-tab (/workflows) and the Runs
    // sub-tab (/workflow-runs). Detail pages under each also qualify.
    matchPaths: [routes.workflows, routes.workflowRuns],
  },
] as const

export const panelNavItems: readonly Pick<NavItem, 'key' | 'to' | 'matchPaths'>[] = appNavItems.map(
  ({ key, to, matchPaths }) => ({ key, to, matchPaths }),
)

/**
 * `true` when `pathname` is covered by any of `matchPaths` — either exact
 * match or a nested path (`/admin-ui/workflows/foo` still activates the
 * `Workflows` entry because `/admin-ui/workflows` is a match prefix).
 */
export function navItemIsActive(matchPaths: readonly string[], pathname: string): boolean {
  return matchPaths.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
}
