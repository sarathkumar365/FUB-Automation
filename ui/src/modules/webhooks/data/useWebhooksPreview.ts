import { useMemo } from 'react'
import { useAppPorts } from '../../../app/useAppPorts'

export function useWebhooksPreview() {
  const { adminWebhookPort } = useAppPorts()

  return useMemo(
    () => ({
      historyPath: '/admin/webhooks',
      detailPathTemplate: '/admin/webhooks/{id}',
      streamPath: adminWebhookPort.buildWebhookStreamRequest({}),
    }),
    [adminWebhookPort],
  )
}
