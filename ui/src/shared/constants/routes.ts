export type AppNavKey = 'webhooks' | 'processedCalls' | 'policies'

export const routes = {
  root: '/',
  adminUi: '/admin-ui',
  webhooks: '/admin-ui/webhooks',
  processedCalls: '/admin-ui/processed-calls',
  policies: '/admin-ui/policies',
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
    key: 'policies' as AppNavKey,
    to: routes.policies,
    railLabel: 'PO',
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
    key: 'policies' as AppNavKey,
    to: routes.policies,
  },
] as const
