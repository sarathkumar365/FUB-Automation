import type { DashboardSnapshot } from '../contracts/dashboardSchemas'

export interface DashboardPort {
  getSnapshot(): Promise<DashboardSnapshot>
}
