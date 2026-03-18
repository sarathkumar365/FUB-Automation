import { HttpAdminWebhookAdapter } from './adapters/http/httpAdminWebhookAdapter'
import { HttpJsonClient } from './adapters/http/httpJsonClient'
import { SseWebhookStreamAdapter } from './adapters/sse/sseWebhookStreamAdapter'
import type { AdminWebhookPort } from './ports/adminWebhookPort'
import type { ProcessedCallsPort } from './ports/processedCallsPort'
import type { WebhookStreamPort } from './ports/webhookStreamPort'

class PlaceholderProcessedCallsAdapter implements ProcessedCallsPort {
  async listProcessedCalls(): Promise<never[]> {
    return []
  }

  async replayProcessedCall(): Promise<{ message: string }> {
    return { message: 'Not implemented in scaffold slice' }
  }
}

const httpClient = new HttpJsonClient()
const adminWebhookPort: AdminWebhookPort = new HttpAdminWebhookAdapter(httpClient)

export type AppPorts = {
  adminWebhookPort: AdminWebhookPort
  processedCallsPort: ProcessedCallsPort
  webhookStreamPort: WebhookStreamPort
}

export const appPorts: AppPorts = {
  adminWebhookPort,
  processedCallsPort: new PlaceholderProcessedCallsAdapter(),
  webhookStreamPort: new SseWebhookStreamAdapter(adminWebhookPort),
}
