import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { PropsWithChildren } from 'react'
import { useState } from 'react'
import { NotifyProvider } from '../shared/notifications/NotifyProvider'
import { AppPortsProvider } from './portsContext'

export function AppProviders({ children }: PropsWithChildren) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            staleTime: 15_000,
          },
        },
      }),
  )

  return (
    <AppPortsProvider>
      <QueryClientProvider client={queryClient}>
        <NotifyProvider>{children}</NotifyProvider>
      </QueryClientProvider>
    </AppPortsProvider>
  )
}
