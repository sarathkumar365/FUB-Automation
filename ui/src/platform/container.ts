import { HttpAdminWebhookAdapter } from './adapters/http/httpAdminWebhookAdapter'
import { HttpDashboardAdapter } from './adapters/http/httpDashboardAdapter'
import { HttpJsonClient } from './adapters/http/httpJsonClient'
import { HttpPersonsAdapter } from './adapters/http/httpPersonsAdapter'
import { HttpProcessedCallsAdapter } from './adapters/http/httpProcessedCallsAdapter'
import { HttpReportingAdapter } from './adapters/http/httpReportingAdapter'
import { HttpSettingsAdapter } from './adapters/http/httpSettingsAdapter'
import { HttpWorkflowAdapter } from './adapters/http/httpWorkflowAdapter'
import { HttpWorkflowRunAdapter } from './adapters/http/httpWorkflowRunAdapter'
import { SseWebhookStreamAdapter } from './adapters/sse/sseWebhookStreamAdapter'
import type { AdminWebhookPort } from './ports/adminWebhookPort'
import type { DashboardPort } from './ports/dashboardPort'
import type { PersonsPort } from './ports/personsPort'
import type { ProcessedCallsPort } from './ports/processedCallsPort'
import type { ReportingPort } from './ports/reportingPort'
import type { SettingsPort } from './ports/settingsPort'
import type { WebhookStreamPort } from './ports/webhookStreamPort'
import type { WorkflowPort } from './ports/workflowPort'
import type { WorkflowRunPort } from './ports/workflowRunPort'

const httpClient = new HttpJsonClient()
const adminWebhookPort: AdminWebhookPort = new HttpAdminWebhookAdapter(httpClient)
const dashboardPort: DashboardPort = new HttpDashboardAdapter(httpClient)
const processedCallsPort: ProcessedCallsPort = new HttpProcessedCallsAdapter(httpClient)
const personsPort: PersonsPort = new HttpPersonsAdapter(httpClient)
const reportingPort: ReportingPort = new HttpReportingAdapter(httpClient)
const settingsPort: SettingsPort = new HttpSettingsAdapter(httpClient)
const workflowPort: WorkflowPort = new HttpWorkflowAdapter(httpClient)
const workflowRunPort: WorkflowRunPort = new HttpWorkflowRunAdapter(httpClient)

export type AppPorts = {
  adminWebhookPort: AdminWebhookPort
  dashboardPort: DashboardPort
  personsPort: PersonsPort
  processedCallsPort: ProcessedCallsPort
  reportingPort: ReportingPort
  settingsPort: SettingsPort
  webhookStreamPort: WebhookStreamPort
  workflowPort: WorkflowPort
  workflowRunPort: WorkflowRunPort
}

export const appPorts: AppPorts = {
  adminWebhookPort,
  dashboardPort,
  personsPort,
  processedCallsPort,
  reportingPort,
  settingsPort,
  webhookStreamPort: new SseWebhookStreamAdapter(adminWebhookPort),
  workflowPort,
  workflowRunPort,
}
