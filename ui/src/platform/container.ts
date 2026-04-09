import { HttpAdminWebhookAdapter } from './adapters/http/httpAdminWebhookAdapter'
import { HttpJsonClient } from './adapters/http/httpJsonClient'
import { HttpPolicyAdapter } from './adapters/http/httpPolicyAdapter'
import { HttpPolicyExecutionAdapter } from './adapters/http/httpPolicyExecutionAdapter'
import { HttpProcessedCallsAdapter } from './adapters/http/httpProcessedCallsAdapter'
import { SseWebhookStreamAdapter } from './adapters/sse/sseWebhookStreamAdapter'
import type { AdminWebhookPort } from './ports/adminWebhookPort'
import type { PolicyExecutionPort } from './ports/policyExecutionPort'
import type { PolicyPort } from './ports/policyPort'
import type { ProcessedCallsPort } from './ports/processedCallsPort'
import type { WebhookStreamPort } from './ports/webhookStreamPort'

const httpClient = new HttpJsonClient()
const adminWebhookPort: AdminWebhookPort = new HttpAdminWebhookAdapter(httpClient)
const processedCallsPort: ProcessedCallsPort = new HttpProcessedCallsAdapter(httpClient)
const policyPort: PolicyPort = new HttpPolicyAdapter(httpClient)
const policyExecutionPort: PolicyExecutionPort = new HttpPolicyExecutionAdapter(httpClient)

export type AppPorts = {
  adminWebhookPort: AdminWebhookPort
  processedCallsPort: ProcessedCallsPort
  webhookStreamPort: WebhookStreamPort
  policyPort: PolicyPort
  policyExecutionPort: PolicyExecutionPort
}

export const appPorts: AppPorts = {
  adminWebhookPort,
  processedCallsPort,
  webhookStreamPort: new SseWebhookStreamAdapter(adminWebhookPort),
  policyPort,
  policyExecutionPort,
}
