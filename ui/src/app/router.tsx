import { createBrowserRouter } from 'react-router-dom'
import { routes } from '../shared/constants/routes'
import { AppShell } from './AppShell'
import { SessionGuard } from './SessionGuard'
import { LandingPage } from '../modules/landing/ui/LandingPage'
import { AuthGuard } from '../modules/auth/ui/AuthGuard'
import { LoginPage } from '../modules/auth/ui/LoginPage'
import { SignupPage } from '../modules/auth/ui/SignupPage'
import { WebhooksPage } from '../modules/webhooks/ui/WebhooksPage'
import { ProcessedCallsPage } from '../modules/processed-calls/ui/ProcessedCallsPage'
import { WorkflowsPage } from '../modules/workflows/ui/WorkflowsPage'
import { WorkflowDetailPage } from '../modules/workflows/ui/WorkflowDetailPage'
import { WorkflowBuilderPage } from '../modules/workflows-builder/ui/WorkflowBuilderPage'
import { WorkflowRunsPage } from '../modules/workflow-runs/ui/WorkflowRunsPage'
import { WorkflowRunDetailPage } from '../modules/workflow-runs/ui/WorkflowRunDetailPage'
import { DashboardPage } from '../modules/dashboard/ui/DashboardPage'
import { PersonsPage } from '../modules/persons/ui/PersonsPage'
import { PersonDetailPage } from '../modules/persons/ui/PersonDetailPage'
import { SessionDisabledPage } from './SessionDisabledPage'
import { NotFoundPage } from './NotFoundPage'
import { RouteErrorBoundary } from './RouteErrorBoundary'

export function createAppRouter() {
  return createBrowserRouter([
    {
      path: routes.root,
      element: <LandingPage />,
      errorElement: <RouteErrorBoundary />,
    },
    {
      path: routes.adminUi,
      element: <AppShell />,
      errorElement: <RouteErrorBoundary />,
      children: [
        {
          path: 'session-disabled',
          element: <SessionDisabledPage />,
        },
        {
          path: 'login',
          element: <LoginPage />,
        },
        {
          path: 'signup',
          element: <SignupPage />,
        },
        {
          element: <SessionGuard />,
          children: [
            {
              element: <AuthGuard />,
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
              path: 'persons',
              element: <PersonsPage />,
            },
            {
              path: 'persons/:sourcePersonId',
              element: <PersonDetailPage />,
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
        {
          // Unknown /admin-ui/* path — 404 inside the shell.
          path: '*',
          element: <NotFoundPage />,
        },
      ],
    },
    {
      // Unknown top-level path — standalone 404.
      path: '*',
      element: <NotFoundPage />,
      errorElement: <RouteErrorBoundary />,
    },
  ])
}
