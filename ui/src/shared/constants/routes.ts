export type AppNavKey = 'webhooks' | 'processedCalls' | 'workflows' | 'workflowRuns'

export const routes = {
  root: '/',
  adminUi: '/admin-ui',
  dashboard: '/admin-ui',
  webhooks: '/admin-ui/webhooks',
  processedCalls: '/admin-ui/processed-calls',
  workflows: '/admin-ui/workflows',
  workflowDetail: (key: string) => `/admin-ui/workflows/${encodeURIComponent(key)}`,
  workflowRuns: '/admin-ui/workflow-runs',
  workflowRunDetail: (runId: number) => `/admin-ui/workflow-runs/${encodeURIComponent(String(runId))}`,
  sessionDisabled: '/admin-ui/session-disabled',
} as const

export const appNavItems = [
  {
    key: 'webhooks' as AppNavKey,
    to: routes.webhooks,
    railLabel: 'WH',
  },
  {
    key: 'processedCalls' as AppNavKey,
    to: routes.processedCalls,
    railLabel: 'PC',
  },
  {
    key: 'workflows' as AppNavKey,
    to: routes.workflows,
    railLabel: 'WF',
  },
  {
    key: 'workflowRuns' as AppNavKey,
    to: routes.workflowRuns,
    railLabel: 'WR',
  },
] as const

export const panelNavItems = [
  {
    key: 'webhooks' as AppNavKey,
    to: routes.webhooks,
  },
  {
    key: 'processedCalls' as AppNavKey,
    to: routes.processedCalls,
  },
  {
    key: 'workflows' as AppNavKey,
    to: routes.workflows,
  },
  {
    key: 'workflowRuns' as AppNavKey,
    to: routes.workflowRuns,
  },
] as const
