import { dashboardSnapshotSchema } from '../../contracts/dashboardSchemas'
import type { DashboardPort } from '../../ports/dashboardPort'
import { HttpJsonClient } from './httpJsonClient'

export class HttpDashboardAdapter implements DashboardPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  getSnapshot() {
    return this.httpClient.get('/admin/dashboard/snapshot', dashboardSnapshotSchema)
  }
}
