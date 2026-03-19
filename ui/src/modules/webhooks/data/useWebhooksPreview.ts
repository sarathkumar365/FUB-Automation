import { useMemo } from 'react'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryDefaults } from '../../../shared/constants/queryDefaults'

export function useWebhooksPreview() {
  const { adminWebhookPort } = useAppPorts()

  return useMemo(
    () => ({
      historyPath: `/admin/webhooks?limit=${queryDefaults.webhooks.limit}`,
      detailPathTemplate: '/admin/webhooks/{id}',
      streamPath: adminWebhookPort.buildWebhookStreamRequest({}),
    }),
    [adminWebhookPort],
  )
}
