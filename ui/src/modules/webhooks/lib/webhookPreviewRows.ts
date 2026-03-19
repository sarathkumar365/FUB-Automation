import { uiText } from '../../../shared/constants/uiText'

export type WebhookPreviewRow = {
  key: string
  label: string
  value: string
}

export function toWebhookPreviewRows(input: {
  historyPath: string
  detailPathTemplate: string
  streamPath: string
}): WebhookPreviewRow[] {
  return [
    {
      key: 'history',
      label: uiText.webhooks.historyEndpointLabel,
      value: input.historyPath,
    },
    {
      key: 'detail',
      label: uiText.webhooks.detailEndpointLabel,
      value: input.detailPathTemplate,
    },
    {
      key: 'stream',
      label: uiText.webhooks.liveStreamEndpointLabel,
      value: input.streamPath,
    },
  ]
}
