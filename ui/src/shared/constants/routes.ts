export type AppNavKey = 'webhooks' | 'processedCalls'

export const routes = {
  root: '/',
  adminUi: '/admin-ui',
  webhooks: '/admin-ui/webhooks',
  processedCalls: '/admin-ui/processed-calls',
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
] as const
