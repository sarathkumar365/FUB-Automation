import { HttpAdminWebhookAdapter } from './adapters/http/httpAdminWebhookAdapter'
import { HttpJsonClient } from './adapters/http/httpJsonClient'
import { HttpProcessedCallsAdapter } from './adapters/http/httpProcessedCallsAdapter'
import { HttpWorkflowAdapter } from './adapters/http/httpWorkflowAdapter'
import { HttpWorkflowRunAdapter } from './adapters/http/httpWorkflowRunAdapter'
import { SseWebhookStreamAdapter } from './adapters/sse/sseWebhookStreamAdapter'
import type { AdminWebhookPort } from './ports/adminWebhookPort'
import type { ProcessedCallsPort } from './ports/processedCallsPort'
import type { WebhookStreamPort } from './ports/webhookStreamPort'
import type { WorkflowPort } from './ports/workflowPort'
import type { WorkflowRunPort } from './ports/workflowRunPort'

const httpClient = new HttpJsonClient()
const adminWebhookPort: AdminWebhookPort = new HttpAdminWebhookAdapter(httpClient)
const processedCallsPort: ProcessedCallsPort = new HttpProcessedCallsAdapter(httpClient)
const workflowPort: WorkflowPort = new HttpWorkflowAdapter(httpClient)
const workflowRunPort: WorkflowRunPort = new HttpWorkflowRunAdapter(httpClient)

export type AppPorts = {
  adminWebhookPort: AdminWebhookPort
  processedCallsPort: ProcessedCallsPort
  webhookStreamPort: WebhookStreamPort
  workflowPort: WorkflowPort
  workflowRunPort: WorkflowRunPort
}

export const appPorts: AppPorts = {
  adminWebhookPort,
  processedCallsPort,
  webhookStreamPort: new SseWebhookStreamAdapter(adminWebhookPort),
  workflowPort,
  workflowRunPort,
}
