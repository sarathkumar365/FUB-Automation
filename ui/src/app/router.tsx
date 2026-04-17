import { createBrowserRouter } from 'react-router-dom'
import { routes } from '../shared/constants/routes'
import { AppShell } from './AppShell'
import { SessionGuard } from './SessionGuard'
import { LandingPage } from '../modules/landing/ui/LandingPage'
import { WebhooksPage } from '../modules/webhooks/ui/WebhooksPage'
import { ProcessedCallsPage } from '../modules/processed-calls/ui/ProcessedCallsPage'
import { WorkflowsPage } from '../modules/workflows/ui/WorkflowsPage'
import { WorkflowDetailPage } from '../modules/workflows/ui/WorkflowDetailPage'
import { WorkflowBuilderPage } from '../modules/workflows-builder/ui/WorkflowBuilderPage'
import { WorkflowRunsPage } from '../modules/workflow-runs/ui/WorkflowRunsPage'
import { WorkflowRunDetailPage } from '../modules/workflow-runs/ui/WorkflowRunDetailPage'
import { DashboardPage } from '../modules/dashboard/ui/DashboardPage'
import { SessionDisabledPage } from './SessionDisabledPage'

export function createAppRouter() {
  return createBrowserRouter([
    {
      path: routes.root,
      element: <LandingPage />,
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
              element: <DashboardPage />,
            },
            {
              path: 'webhooks',
              element: <WebhooksPage />,
            },
            {
              path: 'processed-calls',
              element: <ProcessedCallsPage />,
            },
            {
              path: 'workflows',
              element: <WorkflowsPage />,
            },
            {
              path: 'workflows/new',
              element: <WorkflowBuilderPage />,
            },
            {
              path: 'workflows/:key/edit',
              element: <WorkflowBuilderPage />,
            },
            {
              path: 'workflows/:key',
              element: <WorkflowDetailPage />,
            },
            {
              path: 'workflow-runs',
              element: <WorkflowRunsPage />,
            },
            {
              path: 'workflow-runs/:runId',
              element: <WorkflowRunDetailPage />,
            },
          ],
        },
      ],
    },
  ])
}

export const appRouter = createAppRouter()
