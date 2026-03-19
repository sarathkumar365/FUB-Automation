import { createBrowserRouter, Navigate } from 'react-router-dom'
import { routes } from '../shared/constants/routes'
import { AppShell } from './AppShell'
import { SessionGuard } from './SessionGuard'
import { WebhooksPage } from '../modules/webhooks/ui/WebhooksPage'
import { ProcessedCallsPage } from '../modules/processed-calls/ui/ProcessedCallsPage'
import { SessionDisabledPage } from './SessionDisabledPage'

export const appRouter = createBrowserRouter([
  {
    path: routes.root,
    element: <Navigate to={routes.webhooks} replace />,
  },
  {
    path: routes.adminUi,
    element: <AppShell />,
    children: [
      {
        path: 'session-disabled',
        element: <SessionDisabledPage />,
      },
      {
        element: <SessionGuard />,
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
