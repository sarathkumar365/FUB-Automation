import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppShell } from './AppShell'
import { SessionGuard } from './SessionGuard'
import { WebhooksPage } from '../modules/webhooks/ui/WebhooksPage'
import { ProcessedCallsPage } from '../modules/processed-calls/ui/ProcessedCallsPage'
import { SessionDisabledPage } from './SessionDisabledPage'

export const appRouter = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/admin-ui/webhooks" replace />,
  },
  {
    path: '/admin-ui/session-disabled',
    element: <SessionDisabledPage />,
  },
  {
    element: <SessionGuard />,
    children: [
      {
        path: '/admin-ui',
        element: <AppShell />,
        children: [
          {
            index: true,
            element: <Navigate to="webhooks" replace />,
          },
          {
            path: 'webhooks',
            element: <WebhooksPage />,
          },
          {
            path: 'processed-calls',
            element: <ProcessedCallsPage />,
          },
        ],
      },
    ],
  },
])
