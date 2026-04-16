import type { QueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../platform/query/queryKeys'

export async function invalidateWorkflowMutationQueries(queryClient: QueryClient, key: string) {
  await queryClient.invalidateQueries({
    queryKey: ['workflows', 'list'],
  })
  await queryClient.invalidateQueries({
    queryKey: queryKeys.workflows.detail(key),
  })
  await queryClient.invalidateQueries({
    queryKey: queryKeys.workflows.versions(key),
  })
}

