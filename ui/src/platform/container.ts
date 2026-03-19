import { HttpAdminWebhookAdapter } from './adapters/http/httpAdminWebhookAdapter'
import { HttpJsonClient } from './adapters/http/httpJsonClient'
import { HttpProcessedCallsAdapter } from './adapters/http/httpProcessedCallsAdapter'
import { SseWebhookStreamAdapter } from './adapters/sse/sseWebhookStreamAdapter'
import type { AdminWebhookPort } from './ports/adminWebhookPort'
import type { ProcessedCallsPort } from './ports/processedCallsPort'
import type { WebhookStreamPort } from './ports/webhookStreamPort'

const httpClient = new HttpJsonClient()
const adminWebhookPort: AdminWebhookPort = new HttpAdminWebhookAdapter(httpClient)
const processedCallsPort: ProcessedCallsPort = new HttpProcessedCallsAdapter(httpClient)

export type AppPorts = {
  adminWebhookPort: AdminWebhookPort
  processedCallsPort: ProcessedCallsPort
  webhookStreamPort: WebhookStreamPort
}

export const appPorts: AppPorts = {
  adminWebhookPort,
  processedCallsPort,
  webhookStreamPort: new SseWebhookStreamAdapter(adminWebhookPort),
}
