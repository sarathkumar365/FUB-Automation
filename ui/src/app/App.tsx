import { useMemo } from 'react'
import { RouterProvider } from 'react-router-dom'
import { AppErrorBoundary } from './AppErrorBoundary'
import { AppProviders } from './AppProviders'
import { createAppRouter } from './router'

export default function App() {
  const router = useMemo(() => createAppRouter(), [])

  return (
    <AppErrorBoundary>
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>
    </AppErrorBoundary>
  )
}
